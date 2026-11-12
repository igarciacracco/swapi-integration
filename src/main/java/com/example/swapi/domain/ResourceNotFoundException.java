package com.example.swapi.domain;

/**
 * An unknown id on a detail endpoint, mapped to 404. A list filter with no matches is an empty
 * page instead.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String entity;
    private final String id;

    public ResourceNotFoundException(String entity, String id) {
        super("No %s with id '%s'".formatted(entity, id));
        this.entity = entity;
        this.id = id;
    }

    public String entity() {
        return entity;
    }

    public String id() {
        return id;
    }
}
