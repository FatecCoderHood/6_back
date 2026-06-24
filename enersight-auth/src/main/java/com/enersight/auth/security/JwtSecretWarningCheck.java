package com.enersight.auth.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtSecretWarningCheck {

    private static final String DEV_ONLY_DEFAULT_SECRET = "dev-only-secret-change-me-please-32bytesmin";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    void warnIfUsingDefaultSecret() {
        if (isUsingDefaultSecret()) {
            log.warn("jwt.secret is still set to the dev-only default. This is fine for local "
                    + "development, but MUST be overridden via the JWT_SECRET environment variable "
                    + "in any non-local environment — tokens signed with a known secret can be forged.");
        }
    }

    boolean isUsingDefaultSecret() {
        return DEV_ONLY_DEFAULT_SECRET.equals(jwtSecret);
    }
}
