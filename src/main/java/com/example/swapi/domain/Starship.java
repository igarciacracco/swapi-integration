package com.example.swapi.domain;

public record Starship(String id, String name, String model, String manufacturer,
                       String starshipClass) implements StarWarsResource {}
