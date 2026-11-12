package com.example.swapi.adapter.out.swapi;

import com.example.swapi.adapter.out.swapi.dto.SwapiCollection;
import com.example.swapi.adapter.out.swapi.dto.SwapiItem;
import com.example.swapi.adapter.out.swapi.dto.SwapiPage;
import com.example.swapi.adapter.out.swapi.dto.SwapiSingle;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.InterruptedIOException;
import java.net.http.HttpConnectTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * HTTP calls, JSON envelopes, error translation and caching, shared by the four SWAPI adapters.
 *
 * <p>The cache annotations live here because {@code @Cacheable} only applies to calls that go
 * through the Spring proxy. The adapters call this class as a separate bean, so every call does.
 */
@Component
public class SwapiClient {

    private static final Logger log = LoggerFactory.getLogger(SwapiClient.class);

    static final String PAGES_CACHE = "swapiPages";
    static final String COLLECTIONS_CACHE = "swapiCollections";
    static final String SEARCHES_CACHE = "swapiSearches";
    static final String ITEMS_CACHE = "swapiItems";

    private final RestClient http;

    public SwapiClient(RestClient swapiRestClient) {
        this.http = swapiRestClient;
    }

    /** One page, paginated by SWAPI. Used by people, starships and vehicles. */
    @Cacheable(cacheNames = PAGES_CACHE, key = "#resource + ':' + #page.page() + ':' + #page.size()")
    public SwapiPage getPage(String resource, PageQuery page) {
        return required(execute(() -> http.get()
                .uri(uriBuilder -> uriBuilder.path("/{resource}")
                        .queryParam("page", page.page())
                        .queryParam("limit", page.size())
                        .build(resource))
                .retrieve()
                .body(SwapiPage.class)), resource);
    }

    /** All items in one response. Only films need this: SWAPI ignores paging there. */
    @Cacheable(cacheNames = COLLECTIONS_CACHE, key = "#resource")
    public <P> List<SwapiItem<P>> getAll(String resource,
                                         ParameterizedTypeReference<SwapiCollection<P>> type) {
        return required(execute(() -> http.get()
                .uri("/{resource}", resource)
                .retrieve()
                .body(type)), resource).result();
    }

    /**
     * Name search, unpaginated. {@code searchParam} is {@code title} for films and {@code name}
     * otherwise. The cache key is lower-cased because the upstream search is case-insensitive.
     */
    @Cacheable(cacheNames = SEARCHES_CACHE,
            key = "#resource + ':' + #searchParam + '=' + #value.toLowerCase(T(java.util.Locale).ROOT)")
    public <P> List<SwapiItem<P>> search(String resource, String searchParam, String value,
                                         ParameterizedTypeReference<SwapiCollection<P>> type) {
        return required(execute(() -> http.get()
                .uri(uriBuilder -> uriBuilder.path("/{resource}")
                        .queryParam(searchParam, value)
                        .build(resource))
                .retrieve()
                .body(type)), resource).result();
    }

    /** One entity by id. A 404 becomes empty; any other failure throws. */
    @Cacheable(cacheNames = ITEMS_CACHE, key = "#resource + ':' + #id")
    public <P> Optional<SwapiItem<P>> getOne(String resource, String id,
                                             ParameterizedTypeReference<SwapiSingle<P>> type) {
        SwapiSingle<P> body = executeAllowingMissing(() -> http.get()
                .uri("/{resource}/{id}", resource, id)
                .retrieve()
                .body(type));
        return Optional.ofNullable(body).map(SwapiSingle::result);
    }

    /** Any error status, 404 included, means upstream is unusable. */
    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** Like {@link #execute}, but a 404 returns null. */
    private <T> T executeAllowingMissing(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw translate(e);
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /**
     * Maps every HTTP failure to {@link UpstreamUnavailableException}, flagging timeouts. Upstream
     * details are logged but kept out of the message the client sees.
     */
    private RuntimeException translate(RuntimeException e) {
        if (e instanceof UpstreamUnavailableException alreadyTranslated) {
            return alreadyTranslated;
        }
        if (e instanceof HttpStatusCodeException status) {
            log.warn("SWAPI returned {} for a request; body suppressed", status.getStatusCode());
            return UpstreamUnavailableException.failed(
                    "SWAPI returned " + status.getStatusCode().value(), e);
        }
        if (e instanceof ResourceAccessException access) {
            if (isTimeout(access)) {
                log.warn("SWAPI timed out: {}", access.getMessage());
                return UpstreamUnavailableException.timedOut("SWAPI did not respond in time", e);
            }
            log.warn("SWAPI could not be reached: {}", access.getMessage());
            return UpstreamUnavailableException.failed("SWAPI could not be reached", e);
        }
        if (e instanceof RestClientException) {
            log.warn("SWAPI response could not be read: {}", e.getMessage());
            return UpstreamUnavailableException.failed("SWAPI response could not be read", e);
        }
        return e;
    }

    /** Walks the cause chain; some stacks only signal a timeout in the message. */
    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof HttpConnectTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException
                    || cause instanceof InterruptedIOException) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /** A 200 with an empty body is an upstream failure, not a NullPointerException later. */
    private static <T> T required(T body, String resource) {
        if (body == null) {
            throw UpstreamUnavailableException.failed(
                    "SWAPI returned an empty body for " + resource, null);
        }
        return body;
    }
}
