package com.example.swapi.adapter.out.swapi;

import com.example.swapi.domain.Film;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Summary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.BASE_URL;
import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.fixture;
import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.harness;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The adapter with the quirks, so it gets the most tests.
 *
 * <p>Each test below pins one upstream oddity by name, so a future reader can see what was
 * strange about films without going back to the SWAPI docs.
 */
class SwapiFilmsAdapterTest {

    private SwapiFilmsAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        SwapiAdapterTestSupport.Harness harness = harness();
        this.server = harness.server();
        this.adapter = new SwapiFilmsAdapter(harness.client());
    }

    @Test
    @DisplayName("films are fetched unpaginated and sliced here: page 1")
    void firstPageIsSlicedInMemory() {
        server.expect(requestTo(BASE_URL + "/films"))
                .andRespond(withSuccess(fixture("films-all.json"), MediaType.APPLICATION_JSON));

        PageResult<Summary> result = adapter.list(new PageQuery(1, 2));

        assertThat(result.items()).extracting(Summary::name)
                .containsExactly("A New Hope", "The Empire Strikes Back");
        assertThat(result.totalItems()).isEqualTo(6);
        assertThat(result.totalPages()).isEqualTo(3);
        server.verify();
    }

    @Test
    @DisplayName("page and limit are not sent upstream, because SWAPI ignores them for films")
    void secondPageIsAlsoSlicedFromTheSameCall() {
        server.expect(requestTo(BASE_URL + "/films"))
                .andRespond(withSuccess(fixture("films-all.json"), MediaType.APPLICATION_JSON));

        PageResult<Summary> result = adapter.list(new PageQuery(3, 2));

        assertThat(result.items()).hasSize(2);
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.totalItems()).isEqualTo(6);
        server.verify();
    }

    @Test
    @DisplayName("a page past the last film is empty, not an error")
    void pagePastTheEndIsEmpty() {
        server.expect(requestTo(BASE_URL + "/films"))
                .andRespond(withSuccess(fixture("films-all.json"), MediaType.APPLICATION_JSON));

        assertThat(adapter.list(new PageQuery(9, 10)).items()).isEmpty();
    }

    @Test
    @DisplayName("films are searched by title, not name")
    void searchUsesTheTitleParameter() {
        server.expect(requestTo(BASE_URL + "/films?title=hope"))
                .andRespond(withSuccess(fixture("films-search-hope.json"),
                        MediaType.APPLICATION_JSON));

        List<Summary> matches = adapter.searchByName("hope");

        assertThat(matches).extracting(Summary::name).containsExactly("A New Hope");
        server.verify();
    }

    @Test
    @DisplayName("the upstream title becomes the domain name")
    void detailMapsTitleToName() {
        server.expect(requestTo(BASE_URL + "/films/1"))
                .andRespond(withSuccess(fixture("films-1.json"), MediaType.APPLICATION_JSON));

        Optional<Film> film = adapter.findById("1");

        assertThat(film).hasValueSatisfying(f -> {
            assertThat(f.id()).isEqualTo("1");
            assertThat(f.name()).isEqualTo("A New Hope");
            assertThat(f.episodeId()).isEqualTo(4);
            assertThat(f.director()).isEqualTo("George Lucas");
            assertThat(f.releaseDate()).isEqualTo(LocalDate.of(1977, 5, 25));
        });
        server.verify();
    }

    @Test
    @DisplayName("an unknown id is an empty Optional, not an exception")
    void unknownIdIsEmpty() {
        server.expect(requestTo(BASE_URL + "/films/99"))
                .andRespond(withResourceNotFound());

        assertThat(adapter.findById("99")).isEmpty();
        server.verify();
    }
}
