package com.enersight.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectUserRequestDto {

    @NotBlank
    private String reason;
}
