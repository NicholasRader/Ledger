package com.ledger.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.dto.auth.AuthDtos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all integration tests.
 *
 * Uses a single static PostgreSQLContainer started once for the entire JVM
 * lifetime (manual lifecycle, not @Container). This avoids the problem where
 * @DirtiesContext tears down a Spring context and the next context tries to
 * connect to a Testcontainer that has since been stopped by Ryuk.
 *
 * Because all test classes share the same Spring context (no @DirtiesContext)
 * and the same database, tests use unique emails / UUIDs for isolation.
 * AccountIntegrationTest uses @Transactional to roll back after each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Singleton container - started once, stopped by JVM shutdown hook.
    // Does NOT use @Container / @Testcontainers so Ryuk never stops it
    // between DirtiesContext reloads.
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    protected String registerAndGetToken(String email, String fullName) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return "Bearer " + response.accessToken();
    }

    protected String bearerToken(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }
}