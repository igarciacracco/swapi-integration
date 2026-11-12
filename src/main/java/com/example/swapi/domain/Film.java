package com.example.swapi.domain;

import java.time.LocalDate;

public record Film(String id, String name, int episodeId, String director,
                   LocalDate releaseDate) implements StarWarsResource {}
