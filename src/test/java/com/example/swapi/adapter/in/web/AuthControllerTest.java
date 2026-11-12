package com.example.swapi.adapter.in.web;

import com.example.swapi.config.JwtProperties;
import com.example.swapi.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login against a real BCrypt hash. Tokens are decoded with the application's own
 * {@link JwtDecoder}, so a key or algorithm mismatch between issuing and verifying fails here.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=",
        "app.jwt.ttl=1h",
        "app.jwt.issuer=https://swapi-test.example.com",
        "app.users[0].username=demo",
        // BCrypt hash of "correct-password"
        "app.users[0].password-hash={bcrypt}$2a$10$d28zLN.0iNmHO6zyXvfIWObh7SjNDp6S1Y8GTghDKUQ2ccjbEnCtq"
})
class AuthControllerTest {

    private static final String VALID_LOGIN =
            "{\"username\":\"demo\",\"password\":\"correct-password\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("valid credentials return a bearer token with its lifetime")
    void validCredentialsReturnAToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("the token this service issues is one this service can verify")
    void issuedTokenCarriesTheExpectedClaims() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accessToken");

        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("demo");
        assertThat(decoded.getIssuer()).hasToString("https://swapi-test.example.com");
        assertThat(decoded.<String>getClaim("scope")).isEqualTo("catalog:read");
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt())
                .isCloseTo(Instant.now().plus(jwtProperties.ttl()),
                        within(30, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("the token carries nothing secret, because a JWT is signed but readable")
    void tokenDoesNotCarryTheCredential() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(VALID_LOGIN))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accessToken");
        String decodedPayload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));

        assertThat(decodedPayload)
                .doesNotContain("correct-password")
                .doesNotContain("bcrypt")
                .doesNotContain("$2a$");
    }

    @Test
    @DisplayName("a wrong password is a 401 that does not say which half was wrong")
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    @DisplayName("an unknown user gets the identical 401, so usernames cannot be enumerated")
    void unknownUserIsIndistinguishableFromAWrongPassword() throws Exception {
        String unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"nobody\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPassword);
    }

    @ParameterizedTest(name = "a malformed body is a 400: {0}")
    @ValueSource(strings = {
            "{\"username\":\"\",\"password\":\"x\"}",
            "{\"username\":\"demo\",\"password\":\"\"}",
            "{\"username\":\"demo\"}",
            "{}"
    })
    void malformedLoginBodiesAreBadRequests(String body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("unparseable JSON is a 400, not a 500")
    void unparseableJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    private static org.assertj.core.data.TemporalUnitOffset within(long amount, ChronoUnit unit) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(amount, unit);
    }
}
