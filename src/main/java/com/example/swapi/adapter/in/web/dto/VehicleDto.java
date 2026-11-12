package com.example.swapi.adapter.in.web.dto;

import com.example.swapi.domain.Vehicle;

public record VehicleDto(String id, String name, String model, String manufacturer,
                         String vehicleClass) {

    public static VehicleDto from(Vehicle vehicle) {
        return new VehicleDto(vehicle.id(), vehicle.name(), vehicle.model(),
                vehicle.manufacturer(), vehicle.vehicleClass());
    }
}
