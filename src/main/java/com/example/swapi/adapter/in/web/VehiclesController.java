package com.example.swapi.adapter.in.web;

import com.example.swapi.adapter.in.web.dto.VehicleDto;
import com.example.swapi.adapter.in.web.dto.ListParams;
import com.example.swapi.adapter.in.web.dto.PageResponse;
import com.example.swapi.adapter.in.web.dto.SummaryDto;
import com.example.swapi.application.port.in.StarWarsQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** See {@link PeopleController} for why each entity gets its own controller. */
@Tag(name = "Vehicles", description = "Star Wars vehicles")
@RestController
@RequestMapping(path = "/api/v1/vehicles", produces = MediaType.APPLICATION_JSON_VALUE)
class VehiclesController {

    private final StarWarsQueries queries;

    VehiclesController(StarWarsQueries queries) {
        this.queries = queries;
    }

    @Operation(
            summary = "List vehicles",
            description = "Returns a page of id/name summaries. Supply `id` and/or `name` to "
                    + "filter: `name` is a case-insensitive contains-match, `id` is exact. "
                    + "A filter that matches nothing returns an empty page with status 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of summaries"),
            @ApiResponse(responseCode = "400", description = "page < 1, or size outside 1-100",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "Missing or expired token",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "Token lacks the catalog:read scope",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "502", description = "SWAPI failed",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "504", description = "SWAPI did not answer in time",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping
    PageResponse<SummaryDto> list(@Valid ListParams params) {
        return PageResponse.from(queries.listVehicles(params.filter(), params.pageQuery()));
    }

    @Operation(
            summary = "Fetch one vehicle by id",
            description = "Unlike the list endpoint, an unknown id here is a 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The full record"),
            @ApiResponse(responseCode = "404", description = "No vehicle with that id",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/{id}")
    VehicleDto get(
            @Parameter(description = "SWAPI id, e.g. the id of Sand Crawler", example = "1")
            @PathVariable String id) {
        return VehicleDto.from(queries.getVehicle(id));
    }
}
