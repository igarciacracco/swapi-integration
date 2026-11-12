package com.example.swapi;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End to end: register, log in with the new account, then call the catalog. */
@Tag("docker")
@Testcontainers
@ActiveProfiles("postgres")
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static WireMockServer swapi;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startSwapi() {
        swapi = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        swapi.start();
        swapi.stubFor(get(urlPathEqualTo("/api/people"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture())));
    }

    @AfterAll
    static void stopSwapi() {
        swapi.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("swapi.base-url", () -> swapi.baseUrl() + "/api");
        registry.add("app.jwt.secret",
                () -> "cmVnaXN0cmF0aW9uLXRlc3Qtc2VjcmV0LTMyLWJ5dGVzIQ==");
        registry.add("app.jwt.ttl", () -> "1h");
        registry.add("app.jwt.issuer", () -> "https://swapi-reg.example.com");
        registry.add("app.users[0].username", () -> "demo");
        registry.add("app.users[0].password-hash",
                () -> "{bcrypt}$2a$10$.7J2clSyyeZWzuwz/N/8hOkhVu5c4eWsIi8AEGRs6DpffTaXIbbOK");
    }

    @Test
    @DisplayName("register, then log in with the new account, then read the catalog")
    void registerThenLoginThenUseTheApi() throws Exception {
        String username = "han_solo_" + System.nanoTime();
        String password = "correct-horse-battery-staple";

        // 1. Register: 201, and no token.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // 2. Log in through the same path as the demo user.
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.accessToken");

        // 3. The token works on the catalog.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/people")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Luke Skywalker"));
    }

    @Test
    @DisplayName("the demo user was seeded from configuration and can still log in")
    void seededDemoUserStillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body("demo", "demo-password-2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("registering a taken username is a 409 problem+json")
    void duplicateRegistrationIsAConflict() throws Exception {
        String username = "duplicate_" + System.nanoTime();
        String password = "correct-horse-battery-staple";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body(username, password)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body(username.toUpperCase(java.util.Locale.ROOT), password)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Username taken"));
    }

    @Test
    @DisplayName("a short password is a 400 before anything is stored")
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body("short_pw_user", "tooshort")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("an account that was never registered cannot log in")
    void unregisteredUserCannotLogIn() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body("never_registered", "correct-horse-battery-staple")))
                .andExpect(status().isUnauthorized());
    }

    private static String body(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    private static String fixture() {
        try (InputStream in = RegistrationIntegrationTest.class
                .getResourceAsStream("/swapi/people-page1.json")) {
            return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
