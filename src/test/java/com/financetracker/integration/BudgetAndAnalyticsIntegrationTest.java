package com.financetracker.integration;

import com.financetracker.domain.entity.Account.AccountType;
import com.financetracker.domain.entity.Transaction.TransactionType;
import com.financetracker.dto.account.AccountDtos.*;
import com.financetracker.dto.budget.BudgetDtos.*;
import com.financetracker.dto.transaction.TransactionDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Budget & Analytics Integration Tests")
class BudgetAndAnalyticsIntegrationTest extends BaseIntegrationTest {

    private String token;
    private Long accountId;
    private final LocalDate march2026 = LocalDate.of(2026, 3, 1);

    @BeforeEach
    void setUp() throws Exception {
        String email = "budget-" + UUID.randomUUID() + "@test.com";
        token = registerAndGetToken(email, "Budget User");

        String accountResponse = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Checking", AccountType.CHECKING,
                                        new BigDecimal("5000.00"), "USD"))))
                .andReturn().getResponse().getContentAsString();

        accountId = objectMapper.readTree(accountResponse).get("id").asLong();
    }

    // ── Budget Tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/budgets - should create budget and return spend data")
    void createBudget_returnsSpendData() throws Exception {
        // First add a transaction in this category
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransactionRequest(
                                accountId, 1L, new BigDecimal("150.00"), "USD",
                                TransactionType.EXPENSE, "Groceries", "Whole Foods",
                                LocalDate.of(2026, 3, 10)
                        ))))
                .andExpect(status().isCreated());

        // Create budget for same category
        CreateBudgetRequest budgetRequest = new CreateBudgetRequest(1L, new BigDecimal("400.00"), march2026);

        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(budgetRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountLimit").value(400.00))
                .andExpect(jsonPath("$.categoryId").value(1));
    }

    @Test
    @DisplayName("GET /api/budgets - should show real-time spend against budget")
    void getBudgets_showsRealTimeSpend() throws Exception {
        // Create $250 of expenses in category 1
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransactionRequest(
                                accountId, 1L, new BigDecimal("250.00"), "USD",
                                TransactionType.EXPENSE, "Groceries", "Whole Foods",
                                LocalDate.of(2026, 3, 10)
                        ))))
                .andExpect(status().isCreated());

        // Create $400 budget
        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBudgetRequest(1L, new BigDecimal("400.00"), march2026))))
                .andExpect(status().isCreated());

        // Fetch budgets for March
        mockMvc.perform(get("/api/budgets?month=2026-03-01")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amountSpent").value(250.00))
                .andExpect(jsonPath("$[0].remaining").value(150.00))
                .andExpect(jsonPath("$[0].percentageUsed").value(62.5));
    }

    @Test
    @DisplayName("POST /api/budgets - should return 409 for duplicate budget")
    void createBudget_duplicate_returns409() throws Exception {
        CreateBudgetRequest request = new CreateBudgetRequest(1L, new BigDecimal("400.00"), march2026);

        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second budget for same category/month
        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ── Analytics Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/analytics/cashflow - should reflect actual transactions")
    void cashFlow_reflectsTransactions() throws Exception {
        // Income: $3000
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransactionRequest(
                                accountId, 8L, new BigDecimal("3000.00"), "USD",
                                TransactionType.INCOME, "Salary", "Employer",
                                LocalDate.of(2026, 3, 1)
                        ))))
                .andExpect(status().isCreated());

        // Expense: $500
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransactionRequest(
                                accountId, 1L, new BigDecimal("500.00"), "USD",
                                TransactionType.EXPENSE, "Rent", "Landlord",
                                LocalDate.of(2026, 3, 5)
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/analytics/cashflow?month=2026-03")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(3000.00))
                .andExpect(jsonPath("$.totalExpenses").value(500.00))
                .andExpect(jsonPath("$.netCashFlow").value(2500.00))
                .andExpect(jsonPath("$.month").value("2026-03"));
    }

    @Test
    @DisplayName("GET /api/analytics/networth - should sum all account balances")
    void netWorth_sumsAllAccounts() throws Exception {
        // Create a second account
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Savings", AccountType.SAVINGS,
                                        new BigDecimal("10000.00"), "USD"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/analytics/networth")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNetWorth").value(15000.00))
                .andExpect(jsonPath("$.accountBreakdown.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/analytics/breakdown - should return percentage per category")
    void spendingBreakdown_returnsPercentages() throws Exception {
        // Add $300 food, $100 transport
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransactionRequest(
                                accountId, 1L, new BigDecimal("300.00"), "USD",
                                TransactionType.EXPENSE, "Food", "Restaurant",
                                LocalDate.of(2026, 3, 10)
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransactionRequest(
                                accountId, 2L, new BigDecimal("100.00"), "USD",
                                TransactionType.EXPENSE, "Gas", "Shell",
                                LocalDate.of(2026, 3, 15)
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/analytics/breakdown?start=2026-03-01&end=2026-03-31")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(400.00))
                .andExpect(jsonPath("$.categories.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/analytics/cashflow - should return zeros for month with no transactions")
    void cashFlow_emptyMonth_returnsZeros() throws Exception {
        mockMvc.perform(get("/api/analytics/cashflow?month=2025-01")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(0))
                .andExpect(jsonPath("$.totalExpenses").value(0))
                .andExpect(jsonPath("$.netCashFlow").value(0));
    }

    @Test
    @DisplayName("GET /api/analytics/trends - should return monthly data points")
    void monthlyTrends_returnsDataPoints() throws Exception {
        mockMvc.perform(get("/api/analytics/trends?months=6")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataPoints").isArray());
    }

    @Test
    @DisplayName("GET /api/analytics/burnrate - should calculate daily average")
    void burnRate_calculatesDailyAverage() throws Exception {
        // $310 over March (31 days) = $10/day average
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransactionRequest(
                                accountId, 1L, new BigDecimal("310.00"), "USD",
                                TransactionType.EXPENSE, "Food", "Restaurant",
                                LocalDate.of(2026, 3, 10)
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/analytics/burnrate?categoryId=1&start=2026-03-01&end=2026-03-31")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpend").value(310.00))
                .andExpect(jsonPath("$.dailyAverage").value(10.00))
                .andExpect(jsonPath("$.daysInPeriod").value(31));
    }
}