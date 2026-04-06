package com.ledger.integration;

import com.ledger.dto.account.AccountDtos.*;
import com.ledger.domain.entity.Account.AccountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@DisplayName("Account Controller Integration Tests")
class AccountIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/accounts - should create account and return 201")
    void createAccount_returns201() throws Exception {
        String token = registerAndGetToken("account1@test.com", "Account User");

        CreateAccountRequest request = new CreateAccountRequest(
            "Main Checking", AccountType.CHECKING, new BigDecimal("1000.00"), "USD"
        );

        mockMvc.perform(post("/api/accounts")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Main Checking"))
            .andExpect(jsonPath("$.accountType").value("CHECKING"))
            .andExpect(jsonPath("$.balance").value(1000.00))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/accounts - should return 401 without token")
    void createAccount_withoutToken_returns401() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
            "Checking", AccountType.CHECKING, BigDecimal.ZERO, "USD"
        );

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/accounts - should return all accounts for user")
    void getAccounts_returnsUserAccounts() throws Exception {
        String token = registerAndGetToken("account2@test.com", "Account User 2");

        // Create two accounts
        for (String name : new String[]{"Checking", "Savings"}) {
            mockMvc.perform(post("/api/accounts")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateAccountRequest(name, AccountType.CHECKING, BigDecimal.ZERO, "USD"))))
                .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/accounts")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/accounts - should only return authenticated user's accounts")
    void getAccounts_isolatedByUser() throws Exception {
        String token1 = registerAndGetToken("isolation1@test.com", "User One");
        String token2 = registerAndGetToken("isolation2@test.com", "User Two");

        // User 1 creates an account
        mockMvc.perform(post("/api/accounts")
                .header("Authorization", token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateAccountRequest("User1 Account", AccountType.CHECKING, BigDecimal.ZERO, "USD"))))
            .andExpect(status().isCreated());

        // User 2 should see 0 accounts
        mockMvc.perform(get("/api/accounts")
                .header("Authorization", token2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/accounts/{id} - should return 404 for another user's account")
    void getAccountById_wrongUser_returns404() throws Exception {
        String token1 = registerAndGetToken("owner@test.com", "Owner");
        String token2 = registerAndGetToken("thief@test.com", "Thief");

        // User 1 creates account
        String response = mockMvc.perform(post("/api/accounts")
                .header("Authorization", token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateAccountRequest("Private", AccountType.CHECKING, BigDecimal.ZERO, "USD"))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long accountId = objectMapper.readTree(response).get("id").asLong();

        // User 2 tries to access user 1's account
        mockMvc.perform(get("/api/accounts/" + accountId)
                .header("Authorization", token2))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/accounts/{id} - should delete account and return 204")
    void deleteAccount_returns204() throws Exception {
        String token = registerAndGetToken("delete@test.com", "Delete User");

        String response = mockMvc.perform(post("/api/accounts")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateAccountRequest("To Delete", AccountType.CHECKING, BigDecimal.ZERO, "USD"))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long accountId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/accounts/" + accountId)
                .header("Authorization", token))
            .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/accounts/" + accountId)
                .header("Authorization", token))
            .andExpect(status().isNotFound());
    }
}
