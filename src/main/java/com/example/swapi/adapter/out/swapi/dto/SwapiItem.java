package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entity as SWAPI nests it: the id beside the properties.
 *
 * @param <P> the per-entity property record
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiItem<P>(String uid, P properties) {}
