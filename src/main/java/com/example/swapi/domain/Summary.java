package com.example.swapi.domain;

/** A list row. SWAPI's list endpoints only return id and name. */
public record Summary(String id, String name) {}
