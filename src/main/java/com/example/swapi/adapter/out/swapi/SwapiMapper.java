package com.example.swapi.adapter.out.swapi;

import com.example.swapi.adapter.out.swapi.dto.FilmProperties;
import com.example.swapi.adapter.out.swapi.dto.PersonProperties;
import com.example.swapi.adapter.out.swapi.dto.StarshipProperties;
import com.example.swapi.adapter.out.swapi.dto.SwapiItem;
import com.example.swapi.adapter.out.swapi.dto.SwapiRef;
import com.example.swapi.adapter.out.swapi.dto.VehicleProperties;
import com.example.swapi.domain.Film;
import com.example.swapi.domain.Person;
import com.example.swapi.domain.Starship;
import com.example.swapi.domain.Summary;
import com.example.swapi.domain.Vehicle;

import java.util.List;

/** Converts SWAPI DTOs to domain records. */
final class SwapiMapper {

    private SwapiMapper() {
    }

    static Summary summary(SwapiRef ref) {
        return new Summary(ref.uid(), ref.name());
    }

    static Summary personSummary(SwapiItem<PersonProperties> item) {
        return new Summary(item.uid(), item.properties().name());
    }

    /** Films use {@code title} as their name. */
    static Summary filmSummary(SwapiItem<FilmProperties> item) {
        return new Summary(item.uid(), item.properties().title());
    }

    static Summary starshipSummary(SwapiItem<StarshipProperties> item) {
        return new Summary(item.uid(), item.properties().name());
    }

    static Summary vehicleSummary(SwapiItem<VehicleProperties> item) {
        return new Summary(item.uid(), item.properties().name());
    }

    static Person toPerson(SwapiItem<PersonProperties> item) {
        PersonProperties p = item.properties();
        return new Person(item.uid(), p.name(), p.gender(), p.birthYear(), p.height(), p.mass());
    }

    static Film toFilm(SwapiItem<FilmProperties> item) {
        FilmProperties p = item.properties();
        return new Film(item.uid(), p.title(), p.episodeId(), p.director(), p.releaseDate());
    }

    static Starship toStarship(SwapiItem<StarshipProperties> item) {
        StarshipProperties p = item.properties();
        return new Starship(item.uid(), p.name(), p.model(), p.manufacturer(), p.starshipClass());
    }

    static Vehicle toVehicle(SwapiItem<VehicleProperties> item) {
        VehicleProperties p = item.properties();
        return new Vehicle(item.uid(), p.name(), p.model(), p.manufacturer(), p.vehicleClass());
    }

    static List<Summary> summaries(List<SwapiRef> refs) {
        return refs.stream().map(SwapiMapper::summary).toList();
    }
}
