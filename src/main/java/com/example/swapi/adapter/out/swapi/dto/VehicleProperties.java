package com.example.swapi.adapter.out.swapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleProperties(
        String name,
        String model,
        String manufacturer,
        @JsonProperty("vehicle_class") String vehicleClass) {}
