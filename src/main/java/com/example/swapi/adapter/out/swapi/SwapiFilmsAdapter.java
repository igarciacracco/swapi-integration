package com.example.swapi.adapter.out.swapi;

import com.example.swapi.adapter.out.swapi.dto.FilmProperties;
import com.example.swapi.adapter.out.swapi.dto.SwapiCollection;
import com.example.swapi.adapter.out.swapi.dto.SwapiSingle;
import com.example.swapi.application.port.out.EntityCatalog;
import com.example.swapi.domain.Film;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Summary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Films differ from the other entities in two ways, both handled here:
 *
 * <ul>
 *   <li>{@code GET /films} ignores paging and returns all six, so this adapter slices in memory.
 *   <li>Films are searched and named by {@code title}, not {@code name}.
 * </ul>
 */
@Component
class SwapiFilmsAdapter implements EntityCatalog<Film> {

    private static final String RESOURCE = "films";

    private static final ParameterizedTypeReference<SwapiCollection<FilmProperties>> COLLECTION =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<SwapiSingle<FilmProperties>> SINGLE =
            new ParameterizedTypeReference<>() {
            };

    private final SwapiClient swapi;

    SwapiFilmsAdapter(SwapiClient swapi) {
        this.swapi = swapi;
    }

    @Override
    public PageResult<Summary> list(PageQuery page) {
        List<Summary> all = swapi.getAll(RESOURCE, COLLECTION).stream()
                .map(SwapiMapper::filmSummary)
                .toList();
        return PageResult.slice(all, page);
    }

    @Override
    public List<Summary> searchByName(String name) {
        return swapi.search(RESOURCE, "title", name, COLLECTION).stream()
                .map(SwapiMapper::filmSummary)
                .toList();
    }

    @Override
    public Optional<Film> findById(String id) {
        return swapi.getOne(RESOURCE, id, SINGLE).map(SwapiMapper::toFilm);
    }
}
