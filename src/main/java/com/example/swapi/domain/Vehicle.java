package com.example.swapi.domain;

public record Vehicle(String id, String name, String model, String manufacturer,
                      String vehicleClass) implements StarWarsResource {}
