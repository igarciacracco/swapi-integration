package com.example.swapi.adapter.out.swapi;

import com.example.swapi.adapter.out.swapi.dto.VehicleProperties;
import com.example.swapi.adapter.out.swapi.dto.SwapiCollection;
import com.example.swapi.adapter.out.swapi.dto.SwapiPage;
import com.example.swapi.adapter.out.swapi.dto.SwapiSingle;
import com.example.swapi.application.port.out.EntityCatalog;
import com.example.swapi.domain.PageQuery;
import com.example.swapi.domain.PageResult;
import com.example.swapi.domain.Vehicle;
import com.example.swapi.domain.Summary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Same as {@link SwapiPeopleAdapter}: SWAPI paginates vehicles itself. */
@Component
class SwapiVehiclesAdapter implements EntityCatalog<Vehicle> {

    private static final String RESOURCE = "vehicles";

    private static final ParameterizedTypeReference<SwapiCollection<VehicleProperties>> COLLECTION =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<SwapiSingle<VehicleProperties>> SINGLE =
            new ParameterizedTypeReference<>() {
            };

    private final SwapiClient swapi;

    SwapiVehiclesAdapter(SwapiClient swapi) {
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
                .map(SwapiMapper::vehicleSummary)
                .toList();
    }

    @Override
    public Optional<Vehicle> findById(String id) {
        return swapi.getOne(RESOURCE, id, SINGLE).map(SwapiMapper::toVehicle);
    }
}
