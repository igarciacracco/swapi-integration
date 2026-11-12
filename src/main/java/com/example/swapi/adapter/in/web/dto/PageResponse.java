package com.example.swapi.adapter.in.web.dto;

import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Summary;

import java.util.List;

/** The envelope every list endpoint returns: the page plus enough counts to navigate it. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static PageResponse<SummaryDto> from(PageResult<Summary> result) {
        return new PageResponse<>(
                result.items().stream().map(SummaryDto::from).toList(),
                result.page(),
                result.size(),
                result.totalItems(),
                result.totalPages());
    }
}
