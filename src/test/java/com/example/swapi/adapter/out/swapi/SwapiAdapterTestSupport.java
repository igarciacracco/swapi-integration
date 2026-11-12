package com.example.swapi.adapter.out.swapi;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds a {@code SwapiClient} over a stubbed HTTP layer, with no Spring context. Real JSON
 * decoding still runs. Fixtures in {@code src/test/resources/swapi/} are unedited swapi.tech
 * responses.
 */
final class SwapiAdapterTestSupport {

    static final String BASE_URL = "https://swapi.test/api";

    private SwapiAdapterTestSupport() {
    }

    record Harness(SwapiClient client, MockRestServiceServer server) {
    }

    static Harness harness() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Harness(new SwapiClient(builder.build()), server);
    }

    static String fixture(String name) {
        String path = "/swapi/" + name;
        try (InputStream in = SwapiAdapterTestSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read fixture " + path, e);
        }
    }
}
