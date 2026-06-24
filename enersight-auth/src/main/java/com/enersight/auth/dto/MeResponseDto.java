package com.enersight.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponseDto {

    private UUID id;
    private String email;
    private String name;
    private String phone;
    private List<String> roles;
    private boolean approved;
}
