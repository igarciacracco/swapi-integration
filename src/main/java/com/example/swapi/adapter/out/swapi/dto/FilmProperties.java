package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** SWAPI calls a film's name {@code title}; the mapper converts it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FilmProperties(
        String title,
        @JsonProperty("episode_id") int episodeId,
        String director,
        @JsonProperty("release_date") LocalDate releaseDate) {}
