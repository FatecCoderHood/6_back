package com.enersight.controller;

import com.enersight.dto.AuditLogResponseDto;
import com.enersight.dto.UserPrincipal;
import com.enersight.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService service;

    @GetMapping
    public List<AuditLogResponseDto> list(@AuthenticationPrincipal UserPrincipal currentUser) {
        return service.list(currentUser);
    }
}
