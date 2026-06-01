package com.enersight.dto;

import com.enersight.entity.enums.TermType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TermsOfUseRequestDto {

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    @NotNull
    private TermType type;
}
