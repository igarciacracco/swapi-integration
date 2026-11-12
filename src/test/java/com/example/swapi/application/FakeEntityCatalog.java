package com.example.swapi.application;

import com.example.swapi.application.port.out.EntityCatalog;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.StarWarsResource;
import com.example.swapi.domain.Summary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@code EntityCatalog} for any entity, with the same search semantics as SWAPI.
 * It counts calls, so tests can assert how many lookups the service made.
 */
class FakeEntityCatalog<T extends StarWarsResource> implements EntityCatalog<T> {

    private final Map<String, T> byId = new LinkedHashMap<>();

    private int listCalls;
    private int searchCalls;
    private int findByIdCalls;

    @SafeVarargs
    FakeEntityCatalog(T... resources) {
        for (T resource : resources) {
            byId.put(resource.id(), resource);
        }
    }

    @Override
    public PageResult<Summary> list(PageQuery page) {
        listCalls++;
        List<Summary> all = byId.values().stream().map(StarWarsResource::toSummary).toList();
        return PageResult.slice(all, page);
    }

    @Override
    public List<Summary> searchByName(String name) {
        searchCalls++;
        String needle = name.toLowerCase(Locale.ROOT);
        return byId.values().stream()
                .filter(r -> r.name().toLowerCase(Locale.ROOT).contains(needle))
                .map(StarWarsResource::toSummary)
                .toList();
    }

    @Override
    public Optional<T> findById(String id) {
        findByIdCalls++;
        return Optional.ofNullable(byId.get(id));
    }

    int listCalls() {
        return listCalls;
    }

    int searchCalls() {
        return searchCalls;
    }

    int findByIdCalls() {
        return findByIdCalls;
    }
}
