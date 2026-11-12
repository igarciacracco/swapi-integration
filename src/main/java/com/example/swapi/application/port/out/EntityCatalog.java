package com.example.swapi.application.port.out;

import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.StarWarsResource;
import com.example.swapi.domain.Summary;

import java.util.List;
import java.util.Optional;

/**
 * Output port: what the core needs from a data source for one entity type. Each entity has its
 * own adapter, so upstream quirks stay there instead of becoming branches in the service.
 *
 * @param <T> the entity served; bounded so the service can read {@code id()} and {@code name()}
 */
public interface EntityCatalog<T extends StarWarsResource> {

    /** One page of summaries. The adapter decides whether upstream or memory does the paging. */
    PageResult<Summary> list(PageQuery page);

    /**
     * All case-insensitive name matches. Unpaginated because SWAPI's search is; the service slices
     * the result.
     */
    List<Summary> searchByName(String name);

    /** The full entity, or empty if upstream has no such id. */
    Optional<T> findById(String id);
}
