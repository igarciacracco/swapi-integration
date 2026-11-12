package com.example.swapi.adapter.in.web.dto;

import com.example.swapi.domain.Starship;

public record StarshipDto(String id, String name, String model, String manufacturer,
                          String starshipClass) {

    public static StarshipDto from(Starship starship) {
        return new StarshipDto(starship.id(), starship.name(), starship.model(),
                starship.manufacturer(), starship.starshipClass());
    }
}
