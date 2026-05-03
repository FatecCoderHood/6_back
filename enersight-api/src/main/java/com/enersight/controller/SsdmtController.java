package com.enersight.controller;

import com.enersight.dto.SsdmtGeoDto;
import com.enersight.service.SsdmtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class SsdmtController {

    private final SsdmtService service;

    @GetMapping
    public List<SsdmtGeoDto> getGeoData(
            @RequestParam double minx,
            @RequestParam double miny,
            @RequestParam double maxx,
            @RequestParam double maxy,
            @RequestParam(required = false) Integer year
    ) {
        int resolvedYear = (year != null) ? year : java.time.Year.now().getValue();

        return service.getGeoData(resolvedYear, minx, miny, maxx, maxy);
    }
}