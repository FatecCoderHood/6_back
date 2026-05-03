package com.enersight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsdmtGeoDto {

    private Long conjuntoId;

    // GeoJSON string (recommended for Leaflet)
    private String geom;

    private Double decLimite;
    private Double fecLimite;
    private Double decRealizado;
    private Double fecRealizado;
    private Double desvioDec;
    private Double desvioFec;

    private Double score;
}