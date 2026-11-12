package com.example.swapi.application;

import com.example.swapi.domain.Film;
import com.example.swapi.domain.ListFilter;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Person;
import com.example.swapi.domain.ResourceNotFoundException;
import com.example.swapi.domain.Starship;
import com.example.swapi.domain.Summary;
import com.example.swapi.domain.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Proves the id/name filter table once. Because the rules live in a single generic method,
 * covering them for one entity covers them for all four; the last test checks that the four
 * catalogs really are wired to the four entities and not crossed over.
 */
class StarWarsQueryServiceTest {

    private static final PageQuery FIRST_PAGE = new PageQuery(1, 10);

    private FakeEntityCatalog<Person> people;
    private FakeEntityCatalog<Film> films;
    private FakeEntityCatalog<Starship> starships;
    private FakeEntityCatalog<Vehicle> vehicles;
    private StarWarsQueryService service;

    @BeforeEach
    void setUp() {
        people = new FakeEntityCatalog<>(
                new Person("1", "Luke Skywalker", "male", "19BBY", "172", "77"),
                new Person("2", "C-3PO", "n/a", "112BBY", "167", "75"),
                new Person("4", "Darth Vader", "male", "41.9BBY", "202", "136"));
        films = new FakeEntityCatalog<>(
                new Film("1", "A New Hope", 4, "George Lucas", LocalDate.of(1977, 5, 25)),
                new Film("2", "The Empire Strikes Back", 5, "Irvin Kershner",
                        LocalDate.of(1980, 5, 17)));
        starships = new FakeEntityCatalog<>(
                new Starship("9", "Death Star", "DS-1", "Imperial Dept.", "Deep Space Station"));
        vehicles = new FakeEntityCatalog<>(
                new Vehicle("4", "Sand Crawler", "Digger Crawler", "Corellia", "wheeled"));
        service = new StarWarsQueryService(people, films, starships, vehicles);
    }

    @Nested
    @DisplayName("the id/name filter table")
    class FilterTable {

        @Test
        @DisplayName("no id, no name -> the page the catalog itself returns")
        void neitherFilter() {
            PageResult<Summary> result = service.listPeople(ListFilter.none(), FIRST_PAGE);

            assertThat(result.items()).extracting(Summary::name)
                    .containsExactly("Luke Skywalker", "C-3PO", "Darth Vader");
            assertThat(people.listCalls()).isEqualTo(1);
            assertThat(people.searchCalls()).isZero();
        }

        @Test
        @DisplayName("name only -> search, then slice the matches")
        void nameOnly() {
            PageResult<Summary> result =
                    service.listPeople(new ListFilter(null, "sky"), FIRST_PAGE);

            assertThat(result.items()).extracting(Summary::name).containsExactly("Luke Skywalker");
            assertThat(result.totalItems()).isEqualTo(1);
            assertThat(people.searchCalls()).isEqualTo(1);
            assertThat(people.listCalls()).isZero();
        }

        @Test
        @DisplayName("id only -> one lookup, a page of exactly one")
        void idOnly() {
            PageResult<Summary> result =
                    service.listPeople(new ListFilter("2", null), FIRST_PAGE);

            assertThat(result.items()).containsExactly(new Summary("2", "C-3PO"));
            assertThat(result.totalItems()).isEqualTo(1);
            assertThat(people.findByIdCalls()).isEqualTo(1);
            assertThat(people.listCalls()).isZero();
            assertThat(people.searchCalls()).isZero();
        }

        @Test
        @DisplayName("id + matching name -> that one item")
        void idAndMatchingName() {
            PageResult<Summary> result =
                    service.listPeople(new ListFilter("1", "luke"), FIRST_PAGE);

            assertThat(result.items()).containsExactly(new Summary("1", "Luke Skywalker"));
            assertThat(people.findByIdCalls()).isEqualTo(1);
            assertThat(people.searchCalls()).isZero();
        }

        @Test
        @DisplayName("id + non-matching name -> an empty page, not a 404")
        void idAndMismatchedName() {
            PageResult<Summary> result =
                    service.listPeople(new ListFilter("1", "vader"), FIRST_PAGE);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalItems()).isZero();
            assertThat(result.totalPages()).isZero();
        }

        @Test
        @DisplayName("an id that does not exist -> an empty page, not a 404")
        void unknownIdOnAListIsNotAnError() {
            PageResult<Summary> result =
                    service.listPeople(new ListFilter("999", null), FIRST_PAGE);

            assertThat(result.items()).isEmpty();
        }

        @ParameterizedTest(name = "name search for [{0}] matches {1}")
        @CsvSource({"sky, 1", "SKY, 1", "SkYwAlKeR, 1", "a, 2", "zzz, 0"})
        void nameSearchIsContainsIgnoreCase(String needle, int expectedMatches) {
            PageResult<Summary> result =
                    service.listPeople(new ListFilter(null, needle), FIRST_PAGE);

            assertThat(result.items()).hasSize(expectedMatches);
        }
    }

    @Nested
    @DisplayName("detail lookups")
    class Detail {

        @Test
        void returnsTheFullEntity() {
            assertThat(service.getPerson("1").birthYear()).isEqualTo("19BBY");
            assertThat(service.getFilm("1").episodeId()).isEqualTo(4);
        }

        @Test
        @DisplayName("an unknown id is an error here, unlike on a list")
        void unknownIdThrows() {
            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> service.getFilm("99"))
                    .withMessageContaining("film")
                    .withMessageContaining("99")
                    .satisfies(e -> {
                        assertThat(e.entity()).isEqualTo("film");
                        assertThat(e.id()).isEqualTo("99");
                    });
        }
    }

    @Test
    @DisplayName("each catalog is wired to its own entity, not crossed over")
    void eachEntityUsesItsOwnCatalog() {
        // Person 4 and Vehicle 4 share an id on purpose: a crossed wire would surface here.
        assertThat(service.getPerson("4").name()).isEqualTo("Darth Vader");
        assertThat(service.getVehicle("4").name()).isEqualTo("Sand Crawler");
        assertThat(service.getStarship("9").name()).isEqualTo("Death Star");
        assertThat(service.listFilms(ListFilter.none(), FIRST_PAGE).items())
                .extracting(Summary::name)
                .containsExactly("A New Hope", "The Empire Strikes Back");
    }

    @Test
    @DisplayName("paging applies to search results too")
    void searchResultsArePaged() {
        PageResult<Summary> page2 =
                service.listPeople(new ListFilter(null, "a"), new PageQuery(2, 1));

        assertThat(page2.items()).hasSize(1);
        assertThat(page2.totalItems()).isEqualTo(2);
        assertThat(page2.totalPages()).isEqualTo(2);
    }
}
