package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The detail shape: singular {@code result} holding a single object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiSingle<P>(SwapiItem<P> result) {}
