package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StarshipProperties(
        String name,
        String model,
        String manufacturer,
        @JsonProperty("starship_class") String starshipClass) {}
