package com.example.swapi;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full application with WireMock as SWAPI. Proves the wiring: generic adapter injection,
 * login tokens accepted by the resource server, and upstream failures mapped to 502.
 *
 * <p>WireMock is started manually because its JUnit extension targets JUnit 5 and Boot 4.1 uses
 * JUnit 6.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwapiIntegrationTest {

    private static WireMockServer swapi;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @BeforeAll
    static void startSwapi() {
        swapi = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        swapi.start();
    }

    @AfterAll
    static void stopSwapi() {
        swapi.stop();
    }

    /** Points the SWAPI base URL at the stub. */
    @DynamicPropertySource
    static void swapiProperties(DynamicPropertyRegistry registry) {
        registry.add("swapi.base-url", () -> swapi.baseUrl() + "/api");
        registry.add("swapi.connect-timeout", () -> "1s");
        registry.add("swapi.read-timeout", () -> "1s");
        registry.add("app.jwt.secret",
                () -> "aW50ZWdyYXRpb24tdGVzdC1zZWNyZXQtMzItYnl0ZXMtbG9uZyE=");
        registry.add("app.jwt.ttl", () -> "1h");
        registry.add("app.jwt.issuer", () -> "https://swapi-it.example.com");
        registry.add("app.users[0].username", () -> "demo");
        // BCrypt hash of "integration-password"
        registry.add("app.users[0].password-hash",
                () -> "{bcrypt}$2a$10$UnhX3dUJTnQgTmSSVhICEeEyuygU6uVbNTvaUeK9fDMfmEyhNW1c.");
    }

    @BeforeEach
    void reset() {
        swapi.resetAll();
        cacheManager.getCacheNames().forEach(name ->
                Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    @DisplayName("log in, then use the token on every entity, including the Films special case")
    void fullWalkthrough() throws Exception {
        stub("/api/people", "people-page1.json");
        stub("/api/films", "films-all.json");
        stub("/api/starships", "starships-page1.json");
        stub("/api/vehicles", "vehicles-page1.json");
        stub("/api/people/1", "people-1.json");

        String token = login();

        // People: SWAPI paginates, so the upstream totals come through unchanged.
        mockMvc.perform(authorised("/api/v1/people", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Luke Skywalker"))
                .andExpect(jsonPath("$.totalItems").value(82));

        // Films: SWAPI returns all six and the adapter slices them into the same page shape.
        mockMvc.perform(authorised("/api/v1/films?page=2&size=2", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(6))
                .andExpect(jsonPath("$.totalPages").value(3));

        // Each resolves through a different adapter, proving generic injection.
        mockMvc.perform(authorised("/api/v1/starships", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(36));
        mockMvc.perform(authorised("/api/v1/vehicles", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(39));

        mockMvc.perform(authorised("/api/v1/people/1", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Luke Skywalker"))
                .andExpect(jsonPath("$.birthYear").value("19BBY"));
    }

    @Test
    @DisplayName("filtering by name goes through the search endpoint, not the list endpoint")
    void nameFilterUsesSearch() throws Exception {
        stub("/api/people", "people-search-sky.json");
        String token = login();

        mockMvc.perform(authorised("/api/v1/people?name=sky", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Luke Skywalker"));

        swapi.verify(WireMock.getRequestedFor(urlPathEqualTo("/api/people"))
                .withQueryParam("name", WireMock.equalTo("sky")));
    }

    @Test
    @DisplayName("filtering films by name searches upstream by title")
    void filmNameFilterSearchesByTitle() throws Exception {
        stub("/api/films", "films-search-hope.json");
        String token = login();

        mockMvc.perform(authorised("/api/v1/films?name=hope", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("A New Hope"));

        swapi.verify(WireMock.getRequestedFor(urlPathEqualTo("/api/films"))
                .withQueryParam("title", WireMock.equalTo("hope")));
    }

    @Test
    @DisplayName("an unknown id is a 404; a filter matching nothing is a 200 with an empty page")
    void missingVersusEmpty() throws Exception {
        swapi.stubFor(get(urlPathEqualTo("/api/people/9999"))
                .willReturn(aResponse().withStatus(404)));
        swapi.stubFor(get(urlPathEqualTo("/api/people"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"ok\",\"result\":[]}")));

        String token = login();

        mockMvc.perform(authorised("/api/v1/people/9999", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));

        mockMvc.perform(authorised("/api/v1/people?name=nobody", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("SWAPI being down is a 502, not a stack trace")
    void upstreamDownIsBadGateway() throws Exception {
        swapi.stubFor(get(urlPathEqualTo("/api/people"))
                .willReturn(aResponse().withStatus(500)));
        String token = login();

        mockMvc.perform(authorised("/api/v1/people", token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Upstream unavailable"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("The Star Wars API could not be reached."));
    }

    @Test
    @DisplayName("SWAPI being slow is a 504, because the read timeout fires")
    void upstreamSlowIsGatewayTimeout() throws Exception {
        swapi.stubFor(get(urlPathEqualTo("/api/people"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(3000)));   // read timeout is 1s for this test
        String token = login();

        mockMvc.perform(authorised("/api/v1/people", token))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.title").value("Upstream timeout"));
    }

    @Test
    @DisplayName("without a token nothing in the catalog is reachable")
    void catalogRequiresAToken() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/people"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("health is public, so the host can probe it")
    void healthIsPublic() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("the second identical request is served from cache, not from upstream")
    void repeatedRequestsAreCached() throws Exception {
        stub("/api/people", "people-page1.json");
        String token = login();

        mockMvc.perform(authorised("/api/v1/people", token)).andExpect(status().isOk());
        mockMvc.perform(authorised("/api/v1/people", token)).andExpect(status().isOk());
        mockMvc.perform(authorised("/api/v1/people", token)).andExpect(status().isOk());

        assertThat(swapi.findAll(WireMock.getRequestedFor(urlPathEqualTo("/api/people"))))
                .hasSize(1);
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"integration-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static org.springframework.test.web.servlet.RequestBuilder authorised(String path,
                                                                                  String token) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header("Authorization", "Bearer " + token);
    }

    private static void stub(String path, String fixture) {
        swapi.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(read(fixture))));
    }

    private static String read(String fixture) {
        try (InputStream in = SwapiIntegrationTest.class
                .getResourceAsStream("/swapi/" + fixture)) {
            return new String(Objects.requireNonNull(in, fixture).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read fixture " + fixture, e);
        }
    }
}
