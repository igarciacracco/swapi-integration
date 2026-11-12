package com.example.swapi.domain;

/** The entities this API serves. Sealed, so only these four can back an {@code EntityCatalog}. */
public sealed interface StarWarsResource permits Person, Film, Starship, Vehicle {

    String id();

    /** For films this is SWAPI's {@code title}. */
    String name();

    default Summary toSummary() {
        return new Summary(id(), name());
    }
}
