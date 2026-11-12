package com.example.swapi.adapter.in.web.dto;

import com.example.swapi.domain.Summary;

/** Mirrors {@code Summary} today; separate so a domain change can't silently change the API. */
public record SummaryDto(String id, String name) {

    public static SummaryDto from(Summary summary) {
        return new SummaryDto(summary.id(), summary.name());
    }
}
