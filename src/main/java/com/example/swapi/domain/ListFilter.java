package com.example.swapi.domain;

/** Optional list filters. Blank values become null, so callers only see present or absent. */
public record ListFilter(String id, String name) {

    public ListFilter {
        id = normalise(id);
        name = normalise(name);
    }

    private static String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static ListFilter none() {
        return new ListFilter(null, null);
    }

    public boolean hasId() {
        return id != null;
    }

    public boolean hasName() {
        return name != null;
    }
}
