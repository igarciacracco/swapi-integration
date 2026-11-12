package com.example.swapi.adapter.in.web.dto;

import com.example.swapi.domain.Film;

import java.time.LocalDate;

public record FilmDto(String id, String name, int episodeId, String director,
                      LocalDate releaseDate) {

    public static FilmDto from(Film film) {
        return new FilmDto(film.id(), film.name(), film.episodeId(), film.director(),
                film.releaseDate());
    }
}
