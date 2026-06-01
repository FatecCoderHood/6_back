package com.enersight.controller;

import com.enersight.dto.TermsOfUseRequestDto;
import com.enersight.dto.TermsOfUseResponseDto;
import com.enersight.service.TermsOfUseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermsOfUseController {

    private final TermsOfUseService service;

    @GetMapping
    public List<TermsOfUseResponseDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public TermsOfUseResponseDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TermsOfUseResponseDto create(@Valid @RequestBody TermsOfUseRequestDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public TermsOfUseResponseDto update(@PathVariable UUID id, @Valid @RequestBody TermsOfUseRequestDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
