package com.financetracker.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

// ── Projection DTOs used in repository @Query results ────────────────────────

public class AnalyticsDtos {

    public record CashFlowResponse(
        String month,          // e.g. "2025-03"
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netCashFlow
    ) {}

    public record NetWorthResponse(
        BigDecimal totalNetWorth,
        List<AccountBalance> accountBreakdown
    ) {
        public record AccountBalance(Long accountId, String accountName, BigDecimal balance, String currency) {}
    }

    public record BurnRateResponse(
        Long categoryId,
        String categoryName,
        BigDecimal totalSpend,
        BigDecimal dailyAverage,
        int daysInPeriod
    ) {}

    public record MonthlyTrendResponse(
        List<MonthlyDataPoint> dataPoints
    ) {
        public record MonthlyDataPoint(
            String month,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netCashFlow
        ) {}
    }

    public record SpendingBreakdownResponse(
        String period,
        BigDecimal totalExpenses,
        List<CategoryShare> categories
    ) {
        public record CategoryShare(
            Long categoryId,
            String categoryName,
            BigDecimal amount,
            double percentage
        ) {}
    }
}

// ── JPQL constructor projection ───────────────────────────────────────────────

class CategorySpendDtoHolder {}
