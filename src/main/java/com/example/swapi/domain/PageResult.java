package com.example.swapi.domain;

import java.util.List;

/** One page of results plus the totals a client needs to navigate. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public PageResult {
        items = List.copyOf(items);
    }

    /**
     * Paginates a list held in memory, for SWAPI calls that return everything at once (films, name
     * searches). A page past the end is empty rather than an error, and the totals still describe
     * the whole list.
     */
    public static <T> PageResult<T> slice(List<T> all, PageQuery query) {
        int total = all.size();
        int from = Math.min(query.offset(), total);
        int to = Math.min(from + query.size(), total);
        return new PageResult<>(all.subList(from, to), query.page(), query.size(), total,
                totalPages(total, query.size()));
    }

    /** Wraps a page that upstream already paginated, given its overall total. */
    public static <T> PageResult<T> of(List<T> items, PageQuery query, long totalItems) {
        return new PageResult<>(items, query.page(), query.size(), totalItems,
                totalPages(totalItems, query.size()));
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), query.page(), query.size(), 0, 0);
    }

    private static int totalPages(long totalItems, int size) {
        return (int) Math.ceilDiv(totalItems, size);
    }
}
