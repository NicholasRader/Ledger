package com.financetracker.integration;

import com.financetracker.dto.auth.AuthDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auth endpoint integration tests.
 * Extends BaseIntegrationTest to share the singleton Testcontainer and
 * Spring context with all other integration tests.
 */
@DisplayName("Auth Integration Tests")
class AuthIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/auth/register - should register user and return tokens")
    void register_returnsTokens() throws Exception {
        RegisterRequest request = new RegisterRequest("John Doe", "john@test.com", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("john@test.com"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        AuthResponse response = objectMapper.readValue(body, AuthResponse.class);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("POST /api/auth/register - should return 409 for duplicate email")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane", "jane@test.com", "password123");

        // First registration
        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate
        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/auth/login - should return 401 for bad credentials")
    void login_badCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent@test.com", "wrongpass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/register - should return 400 for invalid payload")
    void register_invalidPayload_returns400() throws Exception {
        // Missing email and short password
        String badPayload = """
            { "fullName": "X", "email": "not-an-email", "password": "abc" }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }
}