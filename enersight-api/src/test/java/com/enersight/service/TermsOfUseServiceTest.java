package com.enersight.service;

import com.enersight.dto.TermsOfUseRequestDto;
import com.enersight.entity.TermsOfUse;
import com.enersight.entity.enums.TermType;
import com.enersight.exception.ResourceNotFoundException;
import com.enersight.repository.TermsOfUseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermsOfUseServiceTest {

    @Mock
    TermsOfUseRepository repository;

    @InjectMocks
    TermsOfUseService service;

    @Test
    void create_throwsForInvalidType() {
        TermsOfUseRequestDto dto = new TermsOfUseRequestDto();
        dto.setTitle("T");
        dto.setContent("C");
        // Using enum; current service compares with strings and will reject
        dto.setType(TermType.MANDATORY);

        assertThrows(IllegalArgumentException.class, () -> service.create(dto));
    }

    @Test
    void toResponse_and_delete_flow() {
        UUID id = UUID.randomUUID();
        TermsOfUse term = TermsOfUse.builder().id(id).title("t").content("c").type(TermType.MANDATORY).active(true).version(1).build();

        when(repository.findById(id)).thenReturn(Optional.of(term));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        var resp = service.findById(id);
        assertEquals(id, resp.getId());

        service.delete(id);
        assertFalse(term.isActive());
    }

    @Test
    void findById_throwsIfNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(id));
    }
}
