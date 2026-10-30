package com.enersight;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIntegrationTest {

    private static final String EMAIL = "flow@example.test";
    private static final String PASSWORD = "flow-password-1";
    // Seeded by V9; weak on purpose of history, tracked as a known gap.
    private static final String ADMIN_EMAIL = "admin@admin.com";
    private static final String ADMIN_PASSWORD = "1234";

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5-alpine").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("enersight_test")
            .withInitScript("db/test-init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_user");
        registry.add("spring.datasource.password", () -> "test-app");
        registry.add("spring.flyway.url", DB::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "flyway_user");
        registry.add("spring.flyway.password", () -> "test-flyway");
    }

    @Autowired
    MockMvc mvc;
    final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void registerApproveLoginAnonymizeAndRegisterAgain() throws Exception {
        List<String> termIds = activeTermIds();

        String userId = register(EMAIL, termIds);
        login(EMAIL, PASSWORD).andExpect(status().isForbidden());

        String adminToken = token(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(get("/api/users").param("status", "PENDING").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='" + EMAIL + "')]", hasSize(1)));
        mvc.perform(patch("/api/users/" + userId + "/approve-access").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String userToken = token(EMAIL, PASSWORD);
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='ANONYMIZE' && @.targetId=='" + userId + "')].actorName")
                        .value(org.hamcrest.Matchers.contains("Anonymized")));
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/" + userId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        register(EMAIL, termIds);
    }

    @Test
    void rejectionNeedsReasonAndBlocksLoginWhileBadCredentialsAreUnauthorized() throws Exception {
        String email = "rejected@example.test";
        List<String> termIds = activeTermIds();
        String userId = register(email, termIds);
        String adminToken = token(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminAuth = "Bearer " + adminToken;

        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content(
                mapper.writeValueAsString(java.util.Map.of(
                        "name", "Dup", "email", email, "password", PASSWORD, "acceptedTermIds", termIds))))
                .andExpect(status().isConflict());

        login(email, "wrong-password-1").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/users/" + userId + "/reject").header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/" + userId + "/reject").header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Incomplete registration\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Incomplete registration"));

        login(email, PASSWORD).andExpect(status().isForbidden());
    }

    private List<String> activeTermIds() throws Exception {
        String body = mvc.perform(get("/api/terms")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return StreamSupport.stream(mapper.readTree(body).spliterator(), false)
                .map(node -> node.get("id").asText()).toList();
    }

    private String register(String email, List<String> termIds) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of(
                "name", "Flow User", "email", email, "password", PASSWORD, "acceptedTermIds", termIds));
        String response = mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).get("id").asText()).toString();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", email, "password", password))));
    }

    private String token(String email, String password) throws Exception {
        String body = login(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(body);
        return node.get("token").asText();
    }
}
