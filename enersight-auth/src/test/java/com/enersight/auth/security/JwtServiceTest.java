package com.enersight.auth.security;

import com.enersight.auth.model.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecretKeySpec secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();

        jwtService = new JwtService(jwtEncoder);
        ReflectionTestUtils.setField(jwtService, "expirationMinutes", 60L);
    }

    private User testUser() {
        return User.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("test@tecsys.com")
                .password("irrelevant")
                .roles(new String[]{"ADMIN"})
                .build();
    }

    @Test
    void generatesTokenThatDecodesWithExpectedClaims() {
        String token = jwtService.generateToken(testUser());

        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("test@tecsys.com");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    @Test
    void rejectsExpiredToken() {
        // Built directly (not via JwtService) since JwtClaimsSet requires expiresAt after issuedAt;
        // here both sit in the past so the token is expired relative to now.
        Instant issuedAt = Instant.now().minus(20, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("enersight-auth")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(5, ChronoUnit.MINUTES))
                .subject("11111111-1111-1111-1111-111111111111")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(expiredToken));
    }

    @Test
    void rejectsTokenWithTamperedSignature() {
        String token = jwtService.generateToken(testUser());
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThrows(BadJwtException.class, () -> jwtDecoder.decode(tampered));
    }
}
