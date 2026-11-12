package com.example.swapi.adapter.out.swapi;

import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Person;
import com.example.swapi.domain.Starship;
import com.example.swapi.domain.Summary;
import com.example.swapi.domain.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Optional;

import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.BASE_URL;
import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.fixture;
import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.harness;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The three adapters that simply forward pagination to SWAPI. They are tested together because
 * they are the same code with a different resource name and entity type: proving one in depth
 * and the other two on their distinguishing details is enough, and it keeps the duplication in
 * the tests proportional to the duplication in the production code.
 */
class SwapiPassThroughAdaptersTest {

    private SwapiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        SwapiAdapterTestSupport.Harness harness = harness();
        this.client = harness.client();
        this.server = harness.server();
    }

    @Nested
    @DisplayName("people")
    class People {

        private SwapiPeopleAdapter adapter;

        @BeforeEach
        void setUp() {
            adapter = new SwapiPeopleAdapter(client);
        }

        @Test
        @DisplayName("page and size are forwarded as SWAPI page and limit")
        void forwardsPagination() {
            server.expect(requestTo(BASE_URL + "/people?page=1&limit=2"))
                    .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                    .andRespond(withSuccess(fixture("people-page1.json"),
                            MediaType.APPLICATION_JSON));

            PageResult<Summary> result = adapter.list(new PageQuery(1, 2));

            assertThat(result.items())
                    .containsExactly(new Summary("1", "Luke Skywalker"), new Summary("2", "C-3PO"));
            server.verify();
        }

        @Test
        @DisplayName("the upstream totals are kept, so totalItems is 82 and not the page size")
        void keepsUpstreamTotals() {
            server.expect(requestTo(BASE_URL + "/people?page=1&limit=2"))
                    .andRespond(withSuccess(fixture("people-page1.json"),
                            MediaType.APPLICATION_JSON));

            PageResult<Summary> result = adapter.list(new PageQuery(1, 2));

            assertThat(result.totalItems()).isEqualTo(82);
            assertThat(result.totalPages()).isEqualTo(41);
        }

        @Test
        @DisplayName("people are searched by name, and all matches come back at once")
        void searchUsesTheNameParameter() {
            server.expect(requestTo(BASE_URL + "/people?name=sky"))
                    .andRespond(withSuccess(fixture("people-search-sky.json"),
                            MediaType.APPLICATION_JSON));

            List<Summary> matches = adapter.searchByName("sky");

            assertThat(matches).extracting(Summary::name)
                    .contains("Luke Skywalker", "Anakin Skywalker");
            server.verify();
        }

        @Test
        @DisplayName("numeric-looking fields stay strings all the way through")
        void detailMapsEveryField() {
            server.expect(requestTo(BASE_URL + "/people/1"))
                    .andRespond(withSuccess(fixture("people-1.json"), MediaType.APPLICATION_JSON));

            Optional<Person> person = adapter.findById("1");

            assertThat(person).hasValueSatisfying(p -> {
                assertThat(p.id()).isEqualTo("1");
                assertThat(p.name()).isEqualTo("Luke Skywalker");
                assertThat(p.gender()).isEqualTo("male");
                assertThat(p.birthYear()).isEqualTo("19BBY");
                assertThat(p.height()).isEqualTo("172");
                assertThat(p.mass()).isEqualTo("77");
            });
        }

        @Test
        void unknownIdIsEmpty() {
            server.expect(requestTo(BASE_URL + "/people/9999"))
                    .andRespond(withResourceNotFound());

            assertThat(adapter.findById("9999")).isEmpty();
        }
    }

    @Nested
    @DisplayName("starships")
    class Starships {

        @Test
        @DisplayName("starship_class is mapped to starshipClass")
        void detailMapsTheClassField() {
            server.expect(requestTo(BASE_URL + "/starships/9"))
                    .andRespond(withSuccess(fixture("starships-9.json"),
                            MediaType.APPLICATION_JSON));

            Optional<Starship> starship = new SwapiStarshipsAdapter(client).findById("9");

            assertThat(starship).hasValueSatisfying(s -> {
                assertThat(s.name()).isEqualTo("Death Star");
                assertThat(s.starshipClass()).isEqualTo("Deep Space Mobile Battlestation");
                assertThat(s.manufacturer()).contains("Imperial Department of Military Research");
            });
        }

        @Test
        void listForwardsPagination() {
            server.expect(requestTo(BASE_URL + "/starships?page=1&limit=2"))
                    .andRespond(withSuccess(fixture("starships-page1.json"),
                            MediaType.APPLICATION_JSON));

            PageResult<Summary> result = new SwapiStarshipsAdapter(client).list(new PageQuery(1, 2));

            assertThat(result.items()).hasSize(2);
            assertThat(result.totalItems()).isEqualTo(36);
            server.verify();
        }
    }

    @Nested
    @DisplayName("vehicles")
    class Vehicles {

        @Test
        @DisplayName("vehicle_class is mapped to vehicleClass")
        void detailMapsTheClassField() {
            server.expect(requestTo(BASE_URL + "/vehicles/4"))
                    .andRespond(withSuccess(fixture("vehicles-4.json"),
                            MediaType.APPLICATION_JSON));

            Optional<Vehicle> vehicle = new SwapiVehiclesAdapter(client).findById("4");

            assertThat(vehicle).hasValueSatisfying(v -> {
                assertThat(v.name()).isEqualTo("Sand Crawler");
                assertThat(v.vehicleClass()).isEqualTo("wheeled");
                assertThat(v.model()).isEqualTo("Digger Crawler");
            });
        }

        @Test
        void listForwardsPagination() {
            server.expect(requestTo(BASE_URL + "/vehicles?page=1&limit=2"))
                    .andRespond(withSuccess(fixture("vehicles-page1.json"),
                            MediaType.APPLICATION_JSON));

            PageResult<Summary> result = new SwapiVehiclesAdapter(client).list(new PageQuery(1, 2));

            assertThat(result.items()).hasSize(2);
            assertThat(result.totalItems()).isEqualTo(39);
            server.verify();
        }
    }
}
