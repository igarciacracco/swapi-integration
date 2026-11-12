package com.example.swapi.domain;

/** Height and mass stay strings: SWAPI returns values like "unknown" and "1,358". */
public record Person(String id, String name, String gender, String birthYear,
                     String height, String mass) implements StarWarsResource {}
