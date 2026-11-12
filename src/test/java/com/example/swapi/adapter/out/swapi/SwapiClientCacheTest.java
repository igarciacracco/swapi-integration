package com.example.swapi.adapter.out.swapi;

import com.example.swapi.config.CacheConfig;
import com.example.swapi.domain.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.swapi.adapter.out.swapi.SwapiAdapterTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves calls go through the cache proxy. A bypassed proxy fails silently (answers stay
 * correct), so these tests count upstream requests instead of checking return values.
 */
@SpringJUnitConfig
@Import({CacheConfig.class, SwapiClientCacheTest.StubbedSwapi.class})
@TestPropertySource(properties = {
        "app.cache.maximum-size=100",
        "app.cache.time-to-live=1h"
})
class SwapiClientCacheTest {

    private static final String BASE_URL = "https://swapi.test/api";

    @Autowired
    private SwapiClient client;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private AtomicInteger upstreamCalls;

    @Autowired
    private CacheManager cacheManager;

    /** The context, cache and counter are shared, so each test resets them. */
    @BeforeEach
    void resetSharedState() {
        server.reset();
        upstreamCalls.set(0);
        cacheManager.getCacheNames().forEach(name ->
                Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    @DisplayName("the same page twice is one upstream call")
    void repeatedPageRequestsHitTheCache() {
        server.expect(once(), requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(withSuccess(fixture("people-page1.json"), MediaType.APPLICATION_JSON));

        client.getPage("people", new PageQuery(1, 10));
        client.getPage("people", new PageQuery(1, 10));
        client.getPage("people", new PageQuery(1, 10));

        assertThat(upstreamCalls).hasValue(1);
        server.verify();
    }

    @Test
    @DisplayName("a different page is a different key, so it really does call upstream")
    void differentPagesAreCachedSeparately() {
        server.expect(once(), requestTo(BASE_URL + "/people?page=1&limit=10"))
                .andRespond(withSuccess(fixture("people-page1.json"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/people?page=2&limit=10"))
                .andRespond(withSuccess(fixture("people-page1.json"), MediaType.APPLICATION_JSON));

        client.getPage("people", new PageQuery(1, 10));
        client.getPage("people", new PageQuery(2, 10));
        client.getPage("people", new PageQuery(1, 10));

        assertThat(upstreamCalls).hasValue(2);
        server.verify();
    }

    @Test
    @DisplayName("a detail lookup is cached per id")
    void detailLookupsAreCachedPerId() {
        server.expect(once(), requestTo(BASE_URL + "/people/1"))
                .andRespond(withSuccess(fixture("people-1.json"), MediaType.APPLICATION_JSON));

        SwapiPeopleAdapter adapter = new SwapiPeopleAdapter(client);
        adapter.findById("1");
        adapter.findById("1");

        assertThat(upstreamCalls).hasValue(1);
        server.verify();
    }

    @Test
    @DisplayName("searches differing only in case share a cache entry, as they do upstream")
    void searchKeysAreCaseInsensitive() {
        server.expect(once(), requestTo(BASE_URL + "/people?name=Luke"))
                .andRespond(withSuccess(fixture("people-search-sky.json"),
                        MediaType.APPLICATION_JSON));

        SwapiPeopleAdapter adapter = new SwapiPeopleAdapter(client);
        adapter.searchByName("Luke");
        adapter.searchByName("luke");
        adapter.searchByName("LUKE");

        assertThat(upstreamCalls).hasValue(1);
        server.verify();
    }

    @TestConfiguration
    static class StubbedSwapi {

        private final AtomicInteger calls = new AtomicInteger();

        @Bean
        AtomicInteger upstreamCalls() {
            return calls;
        }

        @Bean
        RestClient.Builder swapiRestClientBuilder() {
            return RestClient.builder()
                    .baseUrl(BASE_URL)
                    // Counts requests that reach the HTTP layer, i.e. cache misses.
                    .requestInterceptor((request, body, execution) -> {
                        calls.incrementAndGet();
                        return execution.execute(request, body);
                    });
        }

        @Bean
        MockRestServiceServer mockRestServiceServer(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        SwapiClient swapiClient(RestClient.Builder builder, MockRestServiceServer server) {
            // Depends on the server bean so the mock is bound before the client is built.
            return new SwapiClient(builder.build());
        }
    }
}
