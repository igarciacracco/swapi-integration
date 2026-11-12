package com.example.swapi.domain;

/** A 1-based page request. Bounds are checked in the constructor, so an invalid one can't exist. */
public record PageQuery(int page, int size) {

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1, was " + page);
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "size must be " + MIN_SIZE + ".." + MAX_SIZE + ", was " + size);
        }
    }

    /** Zero-based index of the first item on this page. */
    public int offset() {
        return (page - 1) * size;
    }
}
