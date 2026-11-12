package com.example.swapi.adapter.in.web.dto;

import com.example.swapi.domain.ListFilter;
import com.example.swapi.domain.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Query parameters shared by the list endpoints. Validation here gives readable 400s;
 * {@link PageQuery} re-checks the bounds for non-HTTP callers.
 *
 * <p>{@code id} is digits only because it becomes part of the upstream URL path. Blank is
 * allowed: it means "no filter".
 */
public record ListParams(
        @Min(value = 1, message = "page must be 1 or greater") Integer page,
        @Min(value = 1, message = "size must be between 1 and 100")
        @Max(value = 100, message = "size must be between 1 and 100") Integer size,
        @Pattern(regexp = "\\s*(\\d{1,10}\\s*)?", message = "id must be a number") String id,
        @Size(max = 100, message = "name must be at most 100 characters") String name) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    public ListParams {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    public PageQuery pageQuery() {
        return new PageQuery(page, size);
    }

    public ListFilter filter() {
        return new ListFilter(id, name);
    }
}
