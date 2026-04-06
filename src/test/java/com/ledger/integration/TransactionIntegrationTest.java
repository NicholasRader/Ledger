package com.ledger.integration;

import com.ledger.domain.entity.Account.AccountType;
import com.ledger.dto.account.AccountDtos.*;
import com.ledger.dto.transaction.TransactionDtos.*;
import com.ledger.domain.entity.Transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Transaction Controller Integration Tests")
class TransactionIntegrationTest extends BaseIntegrationTest {

    private String token;
    private Long accountId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "txn-" + UUID.randomUUID() + "@test.com";
        token = registerAndGetToken(email, "Transaction User");

        String accountResponse = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Checking", AccountType.CHECKING,
                                        new BigDecimal("1000.00"), "USD"))))
                .andReturn().getResponse().getContentAsString();

        accountId = objectMapper.readTree(accountResponse).get("id").asLong();
    }

    @Test
    @DisplayName("POST /api/transactions - EXPENSE should reduce account balance")
    void createExpense_reducesBalance() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId, 1L, new BigDecimal("150.00"), "USD",
                TransactionType.EXPENSE, "Groceries", "Whole Foods", LocalDate.now()
        );

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.merchant").value("Whole Foods"));

        // Verify balance reduced from 1000 to 850
        mockMvc.perform(get("/api/accounts/" + accountId)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.balance").value(850.00));
    }

    @Test
    @DisplayName("POST /api/transactions - INCOME should increase account balance")
    void createIncome_increasesBalance() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId, 8L, new BigDecimal("2000.00"), "USD",
                TransactionType.INCOME, "Salary", "Employer", LocalDate.now()
        );

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verify balance increased from 1000 to 3000
        mockMvc.perform(get("/api/accounts/" + accountId)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.balance").value(3000.00));
    }

    @Test
    @DisplayName("GET /api/transactions - should return paginated results")
    void getTransactions_paginated() throws Exception {
        // Create 3 transactions
        for (int i = 1; i <= 3; i++) {
            CreateTransactionRequest req = new CreateTransactionRequest(
                    accountId, 1L, new BigDecimal(i * 10), "USD",
                    TransactionType.EXPENSE, "Expense " + i, "Merchant", LocalDate.now()
            );
            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/transactions?page=0&size=10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("DELETE /api/transactions/{id} - should restore account balance")
    void deleteTransaction_restoresBalance() throws Exception {
        // Create expense of $200
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId, 1L, new BigDecimal("200.00"), "USD",
                TransactionType.EXPENSE, "Test", "Merchant", LocalDate.now()
        );

        String txnResponse = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long txnId = objectMapper.readTree(txnResponse).get("id").asLong();

        // Balance should be 800 now
        mockMvc.perform(get("/api/accounts/" + accountId).header("Authorization", token))
                .andExpect(jsonPath("$.balance").value(800.00));

        // Delete the transaction
        mockMvc.perform(delete("/api/transactions/" + txnId)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        // Balance should be restored to 1000
        mockMvc.perform(get("/api/accounts/" + accountId).header("Authorization", token))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    @DisplayName("POST /api/transactions - should return 400 for negative amount")
    void createTransaction_negativeAmount_returns400() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId, 1L, new BigDecimal("-50.00"), "USD",
                TransactionType.EXPENSE, "Bad", "Merchant", LocalDate.now()
        );

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/transactions/{id} - should return 404 for another user's transaction")
    void getTransaction_wrongUser_returns404() throws Exception {
        String otherToken = registerAndGetToken("other.txn@test.com", "Other User");

        // Create transaction as user 1
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId, 1L, new BigDecimal("50.00"), "USD",
                TransactionType.EXPENSE, "Private", "Merchant", LocalDate.now()
        );

        String txnResponse = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        Long txnId = objectMapper.readTree(txnResponse).get("id").asLong();

        // Other user tries to access it
        mockMvc.perform(get("/api/transactions/" + txnId)
                        .header("Authorization", otherToken))
                .andExpect(status().isNotFound());
    }
}