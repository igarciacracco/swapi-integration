package com.example.swapi.adapter.in.web.dto;

import com.example.swapi.domain.Person;

public record PersonDto(String id, String name, String gender, String birthYear,
                        String height, String mass) {

    public static PersonDto from(Person person) {
        return new PersonDto(person.id(), person.name(), person.gender(), person.birthYear(),
                person.height(), person.mass());
    }
}
