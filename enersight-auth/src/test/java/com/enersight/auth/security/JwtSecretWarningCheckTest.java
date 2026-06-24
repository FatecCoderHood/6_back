package com.enersight.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretWarningCheckTest {

    private final JwtSecretWarningCheck check = new JwtSecretWarningCheck();

    @Test
    void flagsTheKnownDevOnlyDefault() {
        ReflectionTestUtils.setField(check, "jwtSecret", "dev-only-secret-change-me-please-32bytesmin");

        assertThat(check.isUsingDefaultSecret()).isTrue();
    }

    @Test
    void doesNotFlagAnOverriddenSecret() {
        ReflectionTestUtils.setField(check, "jwtSecret", "a-real-production-secret-set-via-env-var");

        assertThat(check.isUsingDefaultSecret()).isFalse();
    }
}
