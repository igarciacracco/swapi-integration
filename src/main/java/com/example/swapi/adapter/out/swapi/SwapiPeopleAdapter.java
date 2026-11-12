package com.example.swapi.adapter.out.swapi;

import com.example.swapi.adapter.out.swapi.dto.PersonProperties;
import com.example.swapi.adapter.out.swapi.dto.SwapiCollection;
import com.example.swapi.adapter.out.swapi.dto.SwapiPage;
import com.example.swapi.adapter.out.swapi.dto.SwapiSingle;
import com.example.swapi.application.port.out.EntityCatalog;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Person;
import com.example.swapi.domain.Summary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** SWAPI paginates people itself, so the page is passed straight through. */
@Component
class SwapiPeopleAdapter implements EntityCatalog<Person> {

    private static final String RESOURCE = "people";

    private static final ParameterizedTypeReference<SwapiCollection<PersonProperties>> COLLECTION =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<SwapiSingle<PersonProperties>> SINGLE =
            new ParameterizedTypeReference<>() {
            };

    private final SwapiClient swapi;

    SwapiPeopleAdapter(SwapiClient swapi) {
        this.swapi = swapi;
    }

    @Override
    public PageResult<Summary> list(PageQuery page) {
        SwapiPage response = swapi.getPage(RESOURCE, page);
        return PageResult.of(SwapiMapper.summaries(response.results()), page,
                response.totalRecords());
    }

    @Override
    public List<Summary> searchByName(String name) {
        return swapi.search(RESOURCE, "name", name, COLLECTION).stream()
                .map(SwapiMapper::personSummary)
                .toList();
    }

    @Override
    public Optional<Person> findById(String id) {
        return swapi.getOne(RESOURCE, id, SINGLE).map(SwapiMapper::toPerson);
    }
}
