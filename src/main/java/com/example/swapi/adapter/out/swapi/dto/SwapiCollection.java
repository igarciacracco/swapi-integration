package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Unpaginated list shape (all films, or search matches): singular {@code result}, a list. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiCollection<P>(List<SwapiItem<P>> result) {

    public List<SwapiItem<P>> result() {
        return result == null ? List.of() : result;
    }
}
