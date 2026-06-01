package com.enersight.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDto {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private UserResponseDto user;
}
