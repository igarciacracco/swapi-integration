package com.example.swapi.application.port.in;

import com.example.swapi.domain.Film;
import com.example.swapi.domain.ListFilter;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Person;
import com.example.swapi.domain.Starship;
import com.example.swapi.domain.Summary;
import com.example.swapi.domain.Vehicle;

/**
 * Input port: everything the application offers. Controllers depend on this, not on the service.
 * A new entity adds two methods here; split it per entity if the set grows.
 */
public interface StarWarsQueries {

    PageResult<Summary> listPeople(ListFilter filter, PageQuery page);

    Person getPerson(String id);

    PageResult<Summary> listFilms(ListFilter filter, PageQuery page);

    Film getFilm(String id);

    PageResult<Summary> listStarships(ListFilter filter, PageQuery page);

    Starship getStarship(String id);

    PageResult<Summary> listVehicles(ListFilter filter, PageQuery page);

    Vehicle getVehicle(String id);
}
