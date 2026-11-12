package com.example.swapi.adapter.in.web;

import com.example.swapi.application.port.in.StarWarsQueries;
import com.example.swapi.domain.ListFilter;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Person;
import com.example.swapi.domain.ResourceNotFoundException;
import com.example.swapi.domain.Summary;
import com.example.swapi.domain.UpstreamUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract for the list and detail endpoints: status codes, body shape and validation.
 * Security filters are off here; {@code SecurityRulesTest} covers them.
 */
@WebMvcTest(PeopleController.class)
@AutoConfigureMockMvc(addFilters = false)
class PeopleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StarWarsQueries queries;

    @Test
    @DisplayName("a list returns items plus the paging counts")
    void listReturnsThePageEnvelope() throws Exception {
        given(queries.listPeople(any(), any())).willReturn(new PageResult<>(
                List.of(new Summary("1", "Luke Skywalker")), 1, 10, 82, 9));

        mockMvc.perform(get("/api/v1/people"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.items[0].id").value("1"))
                .andExpect(jsonPath("$.items[0].name").value("Luke Skywalker"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(82))
                .andExpect(jsonPath("$.totalPages").value(9));
    }

    @Test
    @DisplayName("page and size default to 1 and 10 when absent")
    void appliesDefaults() throws Exception {
        given(queries.listPeople(any(), eq(new PageQuery(1, 10))))
                .willReturn(PageResult.empty(new PageQuery(1, 10)));

        mockMvc.perform(get("/api/v1/people")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("id and name reach the service as a filter, trimmed")
    void bindsFilters() throws Exception {
        given(queries.listPeople(eq(new ListFilter("1", "luke")), eq(new PageQuery(2, 25))))
                .willReturn(PageResult.empty(new PageQuery(2, 25)));

        mockMvc.perform(get("/api/v1/people")
                        .param("id", "  1  ")
                        .param("name", "luke")
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a filter matching nothing is an empty page, not a 404")
    void emptyResultIsStillOk() throws Exception {
        given(queries.listPeople(any(), any())).willReturn(PageResult.empty(new PageQuery(1, 10)));

        mockMvc.perform(get("/api/v1/people").param("name", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @ParameterizedTest(name = "{0} is rejected as a problem+json 400")
    @ValueSource(strings = {"size=0", "size=101", "size=500", "page=0", "page=-1",
            "id=abc", "id=1a", "id=../people/1", "id=12345678901"})
    void outOfRangeParametersAreRejected(String query) throws Exception {
        mockMvc.perform(get("/api/v1/people?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("a blank id means no id filter, not a validation error")
    void blankIdIsNoFilter() throws Exception {
        given(queries.listPeople(eq(ListFilter.none()), any()))
                .willReturn(PageResult.empty(new PageQuery(1, 10)));

        mockMvc.perform(get("/api/v1/people").param("id", ""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a non-numeric page is a 400, not a 500")
    void nonNumericPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/people").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a detail lookup returns the full entity")
    void detailReturnsTheEntity() throws Exception {
        given(queries.getPerson("1"))
                .willReturn(new Person("1", "Luke Skywalker", "male", "19BBY", "172", "77"));

        mockMvc.perform(get("/api/v1/people/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.birthYear").value("19BBY"))
                .andExpect(jsonPath("$.height").value("172"));
    }

    @Test
    @DisplayName("an unknown id on a detail endpoint is a 404 problem+json")
    void unknownDetailIdIsNotFound() throws Exception {
        willThrow(new ResourceNotFoundException("person", "9999"))
                .given(queries).getPerson("9999");

        mockMvc.perform(get("/api/v1/people/9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("No person exists with id '9999'"));
    }

    @Test
    @DisplayName("an upstream failure is a 502 that does not leak the cause")
    void upstreamFailureIsBadGateway() throws Exception {
        willThrow(UpstreamUnavailableException.failed(
                "SWAPI returned 500", new IllegalStateException("ORA-00942: table missing")))
                .given(queries).getPerson("1");

        mockMvc.perform(get("/api/v1/people/1"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("The Star Wars API could not be reached."))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("an upstream timeout is a 504, so the client knows a retry may help")
    void upstreamTimeoutIsGatewayTimeout() throws Exception {
        willThrow(UpstreamUnavailableException.timedOut("slow", new RuntimeException()))
                .given(queries).getPerson("1");

        mockMvc.perform(get("/api/v1/people/1"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.title").value("Upstream timeout"));
    }

    @Test
    @DisplayName("an unmapped resource path is a 404 from Spring, not something we wrote")
    void unknownResourcePathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/planets")).andExpect(status().isNotFound());
    }
}
