package com.example.swapi.application;

import com.example.swapi.application.port.in.StarWarsQueries;
import com.example.swapi.application.port.out.EntityCatalog;
import com.example.swapi.domain.Film;
import com.example.swapi.domain.ListFilter;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Person;
import com.example.swapi.domain.ResourceNotFoundException;
import com.example.swapi.domain.StarWarsResource;
import com.example.swapi.domain.Starship;
import com.example.swapi.domain.Summary;
import com.example.swapi.domain.Vehicle;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Implements the input port. Each {@code listX} delegates to {@link #query}, so the id/name
 * filter rules exist once for all four entities. Spring injects each {@code EntityCatalog<T>}
 * by its generic type, so no qualifiers are needed.
 */
@Service
public class StarWarsQueryService implements StarWarsQueries {

    private final EntityCatalog<Person> people;
    private final EntityCatalog<Film> films;
    private final EntityCatalog<Starship> starships;
    private final EntityCatalog<Vehicle> vehicles;

    public StarWarsQueryService(EntityCatalog<Person> people,
                                EntityCatalog<Film> films,
                                EntityCatalog<Starship> starships,
                                EntityCatalog<Vehicle> vehicles) {
        this.people = people;
        this.films = films;
        this.starships = starships;
        this.vehicles = vehicles;
    }

    @Override
    public PageResult<Summary> listPeople(ListFilter filter, PageQuery page) {
        return query(people, filter, page);
    }

    @Override
    public Person getPerson(String id) {
        return get(people, "person", id);
    }

    @Override
    public PageResult<Summary> listFilms(ListFilter filter, PageQuery page) {
        return query(films, filter, page);
    }

    @Override
    public Film getFilm(String id) {
        return get(films, "film", id);
    }

    @Override
    public PageResult<Summary> listStarships(ListFilter filter, PageQuery page) {
        return query(starships, filter, page);
    }

    @Override
    public Starship getStarship(String id) {
        return get(starships, "starship", id);
    }

    @Override
    public PageResult<Summary> listVehicles(ListFilter filter, PageQuery page) {
        return query(vehicles, filter, page);
    }

    @Override
    public Vehicle getVehicle(String id) {
        return get(vehicles, "vehicle", id);
    }

    /**
     * Resolves the optional id and name filters.
     *
     * <table border="1">
     *   <caption>Filter resolution</caption>
     *   <tr><th>id</th><th>name</th><th>behaviour</th></tr>
     *   <tr><td>no</td><td>no</td><td>ask the catalog for the page</td></tr>
     *   <tr><td>no</td><td>yes</td><td>search by name, then slice the matches</td></tr>
     *   <tr><td>yes</td><td>no</td><td>fetch by id: a page of 0 or 1</td></tr>
     *   <tr><td>yes</td><td>yes</td><td>fetch by id, keep it only if the name also matches</td></tr>
     * </table>
     *
     * <p>An id is an exact key, so it takes precedence: one lookup instead of a search. A filter
     * that matches nothing returns an empty page, not a 404.
     */
    private <T extends StarWarsResource> PageResult<Summary> query(EntityCatalog<T> catalog,
                                                                   ListFilter filter,
                                                                   PageQuery page) {
        if (filter.hasId()) {
            List<Summary> match = catalog.findById(filter.id())
                    .filter(resource -> !filter.hasName()
                            || containsIgnoreCase(resource.name(), filter.name()))
                    .map(StarWarsResource::toSummary)
                    .stream()
                    .toList();
            return PageResult.slice(match, page);
        }
        if (filter.hasName()) {
            return PageResult.slice(catalog.searchByName(filter.name()), page);
        }
        return catalog.list(page);
    }

    private <T extends StarWarsResource> T get(EntityCatalog<T> catalog, String entity, String id) {
        return catalog.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(entity, id));
    }

    /** Same semantics as SWAPI's own name search: case-insensitive contains. */
    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null
                && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
