package com.enersight.service;

import com.enersight.dto.SsdmtGeoDto;
import com.enersight.repository.SsdmtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SsdmtService {

    private final SsdmtRepository repository;

    public List<SsdmtGeoDto> getGeoData(
            int year,
            double minx,
            double miny,
            double maxx,
            double maxy
    ) {
        return repository.findGeoData(year, minx, miny, maxx, maxy)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    private SsdmtGeoDto mapToDto(Object[] row) {
        return SsdmtGeoDto.builder()
                .conjuntoId(((Number) row[0]).longValue())
                .geom((String) row[1])
                .decLimite(toDouble(row[2]))
                .fecLimite(toDouble(row[3]))
                .decRealizado(toDouble(row[4]))
                .fecRealizado(toDouble(row[5]))
                .desvioDec(toDouble(row[6]))
                .desvioFec(toDouble(row[7]))
                .score(toDouble(row[8]))
                .build();
    }

    private Double toDouble(Object value) {
        return value != null ? ((Number) value).doubleValue() : null;
    }
}