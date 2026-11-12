package com.example.swapi.adapter.out.swapi;

import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Summary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only test that calls the real SWAPI, excluded from {@code mvn verify}; run with
 * {@code ./mvnw test -Plive}. It checks the contract the adapters rely on (people paginated,
 * films not, films searched by title), not the data.
 */
@Tag("live")
class LiveSwapiContractTest {

    private static SwapiClient liveClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new SwapiClient(RestClient.builder()
                .baseUrl("https://www.swapi.tech/api")
                .requestFactory(factory)
                .defaultHeader("User-Agent", "swapi-integration/1.0 (contract test)")
                .build());
    }

    @Test
    @DisplayName("people are still paginated upstream, with totals we can forward")
    void peopleAreStillPaginated() {
        PageResult<Summary> page = new SwapiPeopleAdapter(liveClient()).list(new PageQuery(1, 2));

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalItems()).isGreaterThan(2);
        assertThat(page.items()).allSatisfy(summary -> {
            assertThat(summary.id()).isNotBlank();
            assertThat(summary.name()).isNotBlank();
        });
    }

    @Test
    @DisplayName("films are still unpaginated, which is why we slice them ourselves")
    void filmsAreStillUnpaginated() {
        SwapiFilmsAdapter adapter = new SwapiFilmsAdapter(liveClient());

        PageResult<Summary> page = adapter.list(new PageQuery(1, 2));

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalItems()).isGreaterThan(2);
    }

    @Test
    @DisplayName("films are still searched by title, people by name")
    void searchParametersAreUnchanged() {
        SwapiClient client = liveClient();

        List<Summary> films = new SwapiFilmsAdapter(client).searchByName("hope");
        List<Summary> people = new SwapiPeopleAdapter(client).searchByName("sky");

        assertThat(films).extracting(Summary::name).anySatisfy(
                name -> assertThat(name).containsIgnoringCase("hope"));
        assertThat(people).extracting(Summary::name).anySatisfy(
                name -> assertThat(name).containsIgnoringCase("sky"));
    }

    @Test
    @DisplayName("an unknown id is still a 404, which we turn into an empty Optional")
    void unknownIdIsStillNotFound() {
        assertThat(new SwapiPeopleAdapter(liveClient()).findById("999999")).isEmpty();
    }
}
