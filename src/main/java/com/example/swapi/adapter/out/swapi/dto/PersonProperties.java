package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonProperties(
        String name,
        String gender,
        @JsonProperty("birth_year") String birthYear,
        String height,
        String mass) {}
