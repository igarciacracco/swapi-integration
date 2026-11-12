package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A paginated list row: SWAPI only returns id, name and URL here. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiRef(String uid, String name, String url) {}
