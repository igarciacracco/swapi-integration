package com.example.swapi.adapter.in.web;

import com.example.swapi.application.port.in.StarWarsQueries;
import com.example.swapi.domain.Film;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.ResourceNotFoundException;
import com.example.swapi.domain.Starship;
import com.example.swapi.domain.Summary;
import com.example.swapi.domain.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Films, starships and vehicles. Shared behaviour is covered in {@code PeopleControllerTest};
 * these check that each path calls its own port method and returns its own typed body.
 */
@WebMvcTest({FilmsController.class, StarshipsController.class, VehiclesController.class})
@AutoConfigureMockMvc(addFilters = false)
class OtherControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StarWarsQueries queries;

    @Test
    @DisplayName("/films is wired to listFilms and renders a film-shaped detail body")
    void filmsEndpoints() throws Exception {
        given(queries.listFilms(any(), any())).willReturn(new PageResult<>(
                List.of(new Summary("1", "A New Hope")), 1, 10, 6, 1));
        given(queries.getFilm("1")).willReturn(
                new Film("1", "A New Hope", 4, "George Lucas", LocalDate.of(1977, 5, 25)));

        mockMvc.perform(get("/api/v1/films"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("A New Hope"))
                .andExpect(jsonPath("$.totalItems").value(6));

        mockMvc.perform(get("/api/v1/films/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A New Hope"))
                .andExpect(jsonPath("$.episodeId").value(4))
                .andExpect(jsonPath("$.director").value("George Lucas"))
                .andExpect(jsonPath("$.releaseDate").value("1977-05-25"));
    }

    @Test
    @DisplayName("/starships renders starshipClass, which is the field name clients see")
    void starshipsEndpoints() throws Exception {
        given(queries.listStarships(any(), any()))
                .willReturn(PageResult.empty(new PageQuery(1, 10)));
        given(queries.getStarship("9")).willReturn(new Starship(
                "9", "Death Star", "DS-1", "Imperial Department", "Deep Space Battlestation"));

        mockMvc.perform(get("/api/v1/starships")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/starships/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Death Star"))
                .andExpect(jsonPath("$.starshipClass").value("Deep Space Battlestation"));
    }

    @Test
    @DisplayName("/vehicles renders vehicleClass")
    void vehiclesEndpoints() throws Exception {
        given(queries.listVehicles(any(), any()))
                .willReturn(PageResult.empty(new PageQuery(1, 10)));
        given(queries.getVehicle("4")).willReturn(
                new Vehicle("4", "Sand Crawler", "Digger Crawler", "Corellia Mining", "wheeled"));

        mockMvc.perform(get("/api/v1/vehicles")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/vehicles/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sand Crawler"))
                .andExpect(jsonPath("$.vehicleClass").value("wheeled"));
    }

    @Test
    @DisplayName("the 404 rule holds for every entity, because it is written once")
    void unknownIdsAreNotFoundEverywhere() throws Exception {
        willThrow(new ResourceNotFoundException("film", "99")).given(queries).getFilm("99");
        willThrow(new ResourceNotFoundException("starship", "99")).given(queries).getStarship("99");
        willThrow(new ResourceNotFoundException("vehicle", "99")).given(queries).getVehicle("99");

        mockMvc.perform(get("/api/v1/films/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No film exists with id '99'"));
        mockMvc.perform(get("/api/v1/starships/99")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/vehicles/99")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("validation applies to every entity, not just people")
    void validationAppliesEverywhere() throws Exception {
        mockMvc.perform(get("/api/v1/films?size=500")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/starships?page=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/vehicles?size=0")).andExpect(status().isBadRequest());
    }
}
