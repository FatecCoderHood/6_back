package com.enersight.security;

import com.enersight.controller.SsdmtController;
import com.enersight.service.SsdmtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real {@link SecurityConfig} beans through MockMvc, without a database:
 * SsdmtService is mocked, so SsdmtRepository never loads. The test token is built with its own
 * Nimbus encoder (enersight-api has no JwtEncoder bean — it only decodes) to prove enersight-api
 * validates tokens issued by anyone holding the shared secret, decoupled from enersight-auth.
 */
@WebMvcTest(SsdmtController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256"
})
class SecurityConfigIntegrationTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SsdmtService ssdmtService;

    private String validToken() {
        SecretKeySpec secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("enersight-auth")
                .issuedAt(now)
                .expiresAt(now.plus(60, ChronoUnit.MINUTES))
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("email", "admin@tecsys.com")
                .claim("roles", List.of("ADMIN"))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void geoEndpointRejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/geo")
                        .param("minx", "0").param("miny", "0").param("maxx", "1").param("maxy", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void geoEndpointAcceptsRequestsWithValidToken() throws Exception {
        when(ssdmtService.getGeoData(anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/geo")
                        .param("minx", "0").param("miny", "0").param("maxx", "1").param("maxy", "1")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk());
    }

    @Test
    void missingRequiredParamYieldsBadRequestNotAnUnauthorizedFromTheErrorForward() throws Exception {
        // A valid token but a missing required @RequestParam fails parameter binding before the
        // controller runs; Spring's default error handling forwards internally to /error to
        // render the body. Asserts that forward still resolves to the real 400, not a 401 from
        // /error itself falling through to anyRequest().authenticated().
        mockMvc.perform(get("/api/geo")
                        .param("miny", "0").param("maxx", "1").param("maxy", "1")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isBadRequest());
    }
}
