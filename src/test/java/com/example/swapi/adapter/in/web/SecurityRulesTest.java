package com.example.swapi.adapter.in.web;

import com.example.swapi.application.port.in.StarWarsQueries;
import com.example.swapi.config.SecurityConfig;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization rules against the real filter chain, using spring-security-test's {@code jwt()}
 * to set scopes. Real token minting is covered by the integration test.
 */
@WebMvcTest(PeopleController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=",
        "app.jwt.ttl=1h",
        "app.jwt.issuer=https://swapi-test.example.com",
        "app.users[0].username=demo",
        "app.users[0].password-hash={noop}irrelevant"
})
class SecurityRulesTest {

    private static final String SCOPE = "catalog:read";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StarWarsQueries queries;

    @ParameterizedTest(name = "{0} without a token is 401")
    @ValueSource(strings = {"/api/v1/people", "/api/v1/people/1"})
    @DisplayName("no token means 401, not 403: we do not know who you are yet")
    void withoutATokenIsUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid token without catalog:read is 403: we know who you are, you may not")
    void wrongScopeIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/people").with(jwt().authorities(() -> "SCOPE_other:scope")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a token with no authorities at all is also 403")
    void noScopeIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/people").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a token with catalog:read gets through")
    void correctScopeIsAllowed() throws Exception {
        given(queries.listPeople(any(), any())).willReturn(PageResult.empty(new PageQuery(1, 10)));

        mockMvc.perform(get("/api/v1/people").with(jwt().authorities(() -> "SCOPE_" + SCOPE)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a garbage bearer token is rejected, not ignored")
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/people").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login is public, so it can be reached without a token")
    void loginIsPublic() throws Exception {
        // Reaching the endpoint is the assertion: 401 would mean the permitAll rule is missing.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(
                                        result.getResponse().getStatus())
                                .isNotEqualTo(403));
    }

    @ParameterizedTest(name = "{0} is public so the host and docs work")
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs"})
    void publicEndpointsStayPublic(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isNotIn(401, 403));
    }

    @Test
    @DisplayName("deny by default: an unmapped path is refused even with a valid token")
    void unmappedPathsAreDeniedByDefault() throws Exception {
        mockMvc.perform(post("/api/v1/people").with(jwt().authorities(() -> "SCOPE_" + SCOPE)))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(
                                        result.getResponse().getStatus())
                                .isIn(403, 405));
    }
}
