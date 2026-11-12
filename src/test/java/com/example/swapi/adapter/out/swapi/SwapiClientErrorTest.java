package com.example.swapi.adapter.out.swapi;

import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.UpstreamUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.BASE_URL;
import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.harness;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Each SWAPI failure mode and the exception it becomes. A 404 on a detail lookup is an empty
 * Optional; on a list endpoint it is a failure. Timeouts are flagged for the 504 mapping.
 */
class SwapiClientErrorTest {

    private SwapiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        SwapiAdapterTestSupport.Harness harness = harness();
        this.client = harness.client();
        this.server = harness.server();
    }

    @ParameterizedTest(name = "upstream {0} becomes a non-timeout failure")
    @ValueSource(ints = {500, 502, 503, 400, 429})
    void anyErrorStatusOnAListBecomesAFailure(int status) {
        server.expect(requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.getPage("people", new PageQuery(1, 10)))
                .satisfies(e -> assertThat(e.isTimeout()).isFalse());
    }

    @Test
    @DisplayName("a 404 on a list endpoint is a failure, not an empty page")
    void notFoundOnAListIsAFailure() {
        server.expect(requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(withResourceNotFound());

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.getPage("people", new PageQuery(1, 10)));
    }

    @Test
    @DisplayName("a read timeout is flagged as a timeout, so the API can answer 504")
    void readTimeoutIsFlagged() {
        server.expect(requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.getPage("people", new PageQuery(1, 10)))
                .satisfies(e -> assertThat(e.isTimeout()).isTrue());
    }

    @Test
    @DisplayName("a connection failure is a failure, not a timeout")
    void connectionFailureIsNotATimeout() {
        server.expect(requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.getPage("people", new PageQuery(1, 10)))
                .satisfies(e -> assertThat(e.isTimeout()).isFalse());
    }

    @Test
    @DisplayName("unreadable JSON is an upstream problem, not a 500 from us")
    void malformedJsonBecomesAnUpstreamFailure() {
        server.expect(requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(withSuccess("{not json at all", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.getPage("people", new PageQuery(1, 10)));
    }

    @Test
    @DisplayName("the exception message never carries the upstream body")
    void upstreamBodyIsNotLeaked() {
        server.expect(requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("ORA-00942: table or view does not exist")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.getPage("people", new PageQuery(1, 10)))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("ORA-00942"));
    }

    @Test
    @DisplayName("a real connect timeout against an unroutable address is flagged as a timeout")
    void connectTimeoutIsFlagged() {
        // TEST-NET-3 (203.0.113.0/24) is unroutable, so this hits a real connect timeout.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(150))
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(150));
        SwapiClient slowClient = new SwapiClient(RestClient.builder()
                .baseUrl("http://203.0.113.1:81/api")
                .requestFactory(factory)
                .build());

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> slowClient.getPage("people", new PageQuery(1, 10)))
                .satisfies(e -> assertThat(e.isTimeout()).isTrue());
    }
}
