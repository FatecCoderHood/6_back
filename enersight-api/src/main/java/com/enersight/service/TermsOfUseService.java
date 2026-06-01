package com.enersight.service;

import com.enersight.dto.TermsOfUseRequestDto;
import com.enersight.dto.TermsOfUseResponseDto;
import com.enersight.entity.TermsOfUse;
import com.enersight.exception.ResourceNotFoundException;
import com.enersight.repository.TermsOfUseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TermsOfUseService {

    private final TermsOfUseRepository repository;

    public TermsOfUseResponseDto create(TermsOfUseRequestDto dto) {

        if (!dto.getType().equals("MANDATORY") && !dto.getType().equals("NON_MANDATORY")) {
            throw new IllegalArgumentException("Ilegal term type: " + dto.getType() + ". Allowed values: MANDATORY, NON_MANDATORY");
        }

        TermsOfUse term = TermsOfUse.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .type(dto.getType())
                .build();
        return toResponse(repository.save(term));
    }

    public List<TermsOfUseResponseDto> findAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TermsOfUseResponseDto findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    public TermsOfUseResponseDto update(UUID id, TermsOfUseRequestDto dto) {
        TermsOfUse term = getOrThrow(id);
        term.setTitle(dto.getTitle());
        term.setContent(dto.getContent());
        term.setType(dto.getType());
        term.setVersion(term.getVersion() + 1);
        return toResponse(repository.save(term));
    }

    public void delete(UUID id) {
        TermsOfUse term = getOrThrow(id);
        term.setActive(false);
        repository.save(term);
    }

    private TermsOfUse getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Term not found: " + id));
    }

    public TermsOfUseResponseDto toResponse(TermsOfUse term) {
        return TermsOfUseResponseDto.builder()
                .id(term.getId())
                .title(term.getTitle())
                .content(term.getContent())
                .type(term.getType())
                .version(term.getVersion())
                .active(term.isActive())
                .createdAt(term.getCreatedAt())
                .build();
    }
}
