package com.financetracker.service;

import com.financetracker.domain.entity.Account;
import com.financetracker.domain.entity.Account.AccountType;
import com.financetracker.domain.entity.User;
import com.financetracker.domain.repository.AccountRepository;
import com.financetracker.domain.repository.TransactionRepository;
import com.financetracker.dto.analytics.AnalyticsDtos.*;
import com.financetracker.dto.analytics.CategorySpendDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService Unit Tests")
class AnalyticsServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @InjectMocks private AnalyticsService analyticsService;

    private User testUser;
    private Account checkingAccount;
    private Account savingsAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L).email("test@example.com").fullName("Test").role("ROLE_USER")
            .build();

        checkingAccount = Account.builder()
            .id(1L).user(testUser).name("Checking")
            .accountType(AccountType.CHECKING)
            .balance(new BigDecimal("2500.00")).currency("USD")
            .build();
        ReflectionTestUtils.setField(checkingAccount, "createdAt", LocalDateTime.now());

        savingsAccount = Account.builder()
            .id(2L).user(testUser).name("Savings")
            .accountType(AccountType.SAVINGS)
            .balance(new BigDecimal("10000.00")).currency("USD")
            .build();
        ReflectionTestUtils.setField(savingsAccount, "createdAt", LocalDateTime.now());
    }

    // ── Cash Flow ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCashFlow()")
    class GetCashFlow {

        @Test
        @DisplayName("should return correct net cash flow for a month")
        void getCashFlow_correctNet() {
            YearMonth march = YearMonth.of(2026, 3);
            LocalDate start = march.atDay(1);
            LocalDate end = march.atEndOfMonth();

            when(transactionRepository.sumIncomeByUserAndDateRange(1L, start, end))
                .thenReturn(new BigDecimal("3000.00"));
            when(transactionRepository.sumExpensesByUserAndDateRange(1L, start, end))
                .thenReturn(new BigDecimal("1200.00"));

            CashFlowResponse response = analyticsService.getCashFlow(1L, march);

            assertThat(response.totalIncome()).isEqualByComparingTo("3000.00");
            assertThat(response.totalExpenses()).isEqualByComparingTo("1200.00");
            assertThat(response.netCashFlow()).isEqualByComparingTo("1800.00");
            assertThat(response.month()).isEqualTo("2026-03");
        }

        @Test
        @DisplayName("should return negative net cash flow when expenses exceed income")
        void getCashFlow_negativeNet() {
            YearMonth march = YearMonth.of(2026, 3);
            LocalDate start = march.atDay(1);
            LocalDate end = march.atEndOfMonth();

            when(transactionRepository.sumIncomeByUserAndDateRange(1L, start, end))
                .thenReturn(new BigDecimal("500.00"));
            when(transactionRepository.sumExpensesByUserAndDateRange(1L, start, end))
                .thenReturn(new BigDecimal("1500.00"));

            CashFlowResponse response = analyticsService.getCashFlow(1L, march);

            assertThat(response.netCashFlow()).isNegative();
            assertThat(response.netCashFlow()).isEqualByComparingTo("-1000.00");
        }

        @Test
        @DisplayName("should return zero net cash flow when income equals expenses")
        void getCashFlow_zeroNet() {
            YearMonth march = YearMonth.of(2026, 3);
            LocalDate start = march.atDay(1);
            LocalDate end = march.atEndOfMonth();

            when(transactionRepository.sumIncomeByUserAndDateRange(1L, start, end))
                .thenReturn(new BigDecimal("1000.00"));
            when(transactionRepository.sumExpensesByUserAndDateRange(1L, start, end))
                .thenReturn(new BigDecimal("1000.00"));

            CashFlowResponse response = analyticsService.getCashFlow(1L, march);

            assertThat(response.netCashFlow()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("should handle zero income and zero expenses")
        void getCashFlow_allZero() {
            YearMonth march = YearMonth.of(2026, 3);
            LocalDate start = march.atDay(1);
            LocalDate end = march.atEndOfMonth();

            when(transactionRepository.sumIncomeByUserAndDateRange(1L, start, end))
                .thenReturn(BigDecimal.ZERO);
            when(transactionRepository.sumExpensesByUserAndDateRange(1L, start, end))
                .thenReturn(BigDecimal.ZERO);

            CashFlowResponse response = analyticsService.getCashFlow(1L, march);

            assertThat(response.netCashFlow()).isEqualByComparingTo("0.00");
        }
    }

    // ── Net Worth ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getNetWorth()")
    class GetNetWorth {

        @Test
        @DisplayName("should sum balances across all accounts")
        void getNetWorth_sumsAllAccounts() {
            when(accountRepository.sumBalanceByUserId(1L))
                .thenReturn(new BigDecimal("12500.00"));
            when(accountRepository.findByUserId(1L))
                .thenReturn(List.of(checkingAccount, savingsAccount));

            NetWorthResponse response = analyticsService.getNetWorth(1L);

            assertThat(response.totalNetWorth()).isEqualByComparingTo("12500.00");
            assertThat(response.accountBreakdown()).hasSize(2);
        }

        @Test
        @DisplayName("should include per-account breakdown")
        void getNetWorth_includesBreakdown() {
            when(accountRepository.sumBalanceByUserId(1L))
                .thenReturn(new BigDecimal("12500.00"));
            when(accountRepository.findByUserId(1L))
                .thenReturn(List.of(checkingAccount, savingsAccount));

            NetWorthResponse response = analyticsService.getNetWorth(1L);

            assertThat(response.accountBreakdown())
                .extracting(NetWorthResponse.AccountBalance::accountName)
                .containsExactlyInAnyOrder("Checking", "Savings");
            assertThat(response.accountBreakdown())
                .extracting(NetWorthResponse.AccountBalance::balance)
                .containsExactlyInAnyOrder(
                    new BigDecimal("2500.00"),
                    new BigDecimal("10000.00")
                );
        }

        @Test
        @DisplayName("should return zero net worth when no accounts exist")
        void getNetWorth_noAccounts() {
            when(accountRepository.sumBalanceByUserId(1L)).thenReturn(BigDecimal.ZERO);
            when(accountRepository.findByUserId(1L)).thenReturn(List.of());

            NetWorthResponse response = analyticsService.getNetWorth(1L);

            assertThat(response.totalNetWorth()).isEqualByComparingTo("0.00");
            assertThat(response.accountBreakdown()).isEmpty();
        }
    }

    // ── Burn Rate ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getBurnRate()")
    class GetBurnRate {

        @Test
        @DisplayName("should calculate daily average correctly")
        void getBurnRate_correctDailyAverage() {
            // 31 days in March, $310 spend = $10/day
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);
            CategorySpendDto spend = new CategorySpendDto(1L, "Food & Dining", new BigDecimal("310.00"));

            when(transactionRepository.sumExpensesByCategoryAndDateRange(1L, start, end))
                .thenReturn(List.of(spend));

            BurnRateResponse response = analyticsService.getBurnRate(1L, 1L, start, end);

            assertThat(response.totalSpend()).isEqualByComparingTo("310.00");
            assertThat(response.dailyAverage()).isEqualByComparingTo("10.00");
            assertThat(response.daysInPeriod()).isEqualTo(31);
        }

        @Test
        @DisplayName("should return zero daily average when no spend in category")
        void getBurnRate_noSpend() {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);

            when(transactionRepository.sumExpensesByCategoryAndDateRange(1L, start, end))
                .thenReturn(List.of());

            BurnRateResponse response = analyticsService.getBurnRate(1L, 1L, start, end);

            assertThat(response.totalSpend()).isEqualByComparingTo("0.00");
            assertThat(response.dailyAverage()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("should correctly count days in period")
        void getBurnRate_correctDayCount() {
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 1, 10);  // 10 days

            when(transactionRepository.sumExpensesByCategoryAndDateRange(1L, start, end))
                .thenReturn(List.of());

            BurnRateResponse response = analyticsService.getBurnRate(1L, 1L, start, end);

            assertThat(response.daysInPeriod()).isEqualTo(10);
        }
    }

    // ── Monthly Trends ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMonthlyTrends()")
    class GetMonthlyTrends {

        @Test
        @DisplayName("should return data points for each month")
        void getMonthlyTrends_returnsDataPoints() {
            Object[] jan = new Object[]{"2026-01", new BigDecimal("2000.00"), new BigDecimal("800.00")};
            Object[] feb = new Object[]{"2026-02", new BigDecimal("2500.00"), new BigDecimal("1000.00")};
            Object[] mar = new Object[]{"2026-03", new BigDecimal("3000.00"), new BigDecimal("1200.00")};

            when(transactionRepository.findMonthlyTrends(eq(1L), any()))
                .thenReturn(List.of(jan, feb, mar));

            MonthlyTrendResponse response = analyticsService.getMonthlyTrends(1L, 3);

            assertThat(response.dataPoints()).hasSize(3);
            assertThat(response.dataPoints().get(0).month()).isEqualTo("2026-01");
            assertThat(response.dataPoints().get(0).totalIncome()).isEqualByComparingTo("2000.00");
            assertThat(response.dataPoints().get(0).totalExpenses()).isEqualByComparingTo("800.00");
            assertThat(response.dataPoints().get(0).netCashFlow()).isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("should return empty list when no transaction history")
        void getMonthlyTrends_noHistory() {
            when(transactionRepository.findMonthlyTrends(eq(1L), any()))
                .thenReturn(List.of());

            MonthlyTrendResponse response = analyticsService.getMonthlyTrends(1L, 6);

            assertThat(response.dataPoints()).isEmpty();
        }

        @Test
        @DisplayName("should correctly calculate net cash flow per month")
        void getMonthlyTrends_netCashFlowCalculation() {
            // Income 1000, expenses 1500 = net -500
            Object[] row = new Object[]{"2026-03", new BigDecimal("1000.00"), new BigDecimal("1500.00")};

            when(transactionRepository.findMonthlyTrends(eq(1L), any()))
                .thenReturn(List.of(row));

            MonthlyTrendResponse response = analyticsService.getMonthlyTrends(1L, 1);

            assertThat(response.dataPoints().get(0).netCashFlow())
                .isEqualByComparingTo("-500.00");
        }
    }

    // ── Spending Breakdown ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSpendingBreakdown()")
    class GetSpendingBreakdown {

        @Test
        @DisplayName("should calculate correct percentage per category")
        void getSpendingBreakdown_correctPercentages() {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);

            // Food = $300, Transport = $100 → total $400
            List<CategorySpendDto> spends = List.of(
                new CategorySpendDto(1L, "Food & Dining", new BigDecimal("300.00")),
                new CategorySpendDto(2L, "Transportation", new BigDecimal("100.00"))
            );

            when(transactionRepository.sumExpensesByCategoryAndDateRange(1L, start, end))
                .thenReturn(spends);

            SpendingBreakdownResponse response = analyticsService.getSpendingBreakdown(1L, start, end);

            assertThat(response.totalExpenses()).isEqualByComparingTo("400.00");
            assertThat(response.categories()).hasSize(2);

            SpendingBreakdownResponse.CategoryShare food = response.categories().stream()
                .filter(c -> c.categoryName().equals("Food & Dining")).findFirst().orElseThrow();
            SpendingBreakdownResponse.CategoryShare transport = response.categories().stream()
                .filter(c -> c.categoryName().equals("Transportation")).findFirst().orElseThrow();

            assertThat(food.percentage()).isEqualTo(75.0);
            assertThat(transport.percentage()).isEqualTo(25.0);
        }

        @Test
        @DisplayName("should return empty breakdown when no expenses")
        void getSpendingBreakdown_noExpenses() {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);

            when(transactionRepository.sumExpensesByCategoryAndDateRange(1L, start, end))
                .thenReturn(List.of());

            SpendingBreakdownResponse response = analyticsService.getSpendingBreakdown(1L, start, end);

            assertThat(response.totalExpenses()).isEqualByComparingTo("0.00");
            assertThat(response.categories()).isEmpty();
        }

        @Test
        @DisplayName("should handle single category at 100%")
        void getSpendingBreakdown_singleCategory() {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);

            when(transactionRepository.sumExpensesByCategoryAndDateRange(1L, start, end))
                .thenReturn(List.of(
                    new CategorySpendDto(1L, "Food & Dining", new BigDecimal("500.00"))
                ));

            SpendingBreakdownResponse response = analyticsService.getSpendingBreakdown(1L, start, end);

            assertThat(response.categories().get(0).percentage()).isEqualTo(100.0);
        }
    }
}
