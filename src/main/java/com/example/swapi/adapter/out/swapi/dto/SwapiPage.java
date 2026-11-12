package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Paginated list shape (people, starships, vehicles). Note {@code results}, plural. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwapiPage(
        @JsonProperty("total_records") long totalRecords,
        @JsonProperty("total_pages") int totalPages,
        List<SwapiRef> results) {

    public List<SwapiRef> results() {
        return results == null ? List.of() : results;
    }
}
