package com.ledger.dto.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BudgetDtos {

    public record CreateBudgetRequest(
        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotNull(message = "Amount limit is required")
        @Positive(message = "Amount limit must be positive")
        BigDecimal amountLimit,

        @NotNull(message = "Month is required (use first day of month, e.g. 2025-03-01)")
        LocalDate month
    ) {}

    public record UpdateBudgetRequest(
        @Positive BigDecimal amountLimit
    ) {}

    public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        BigDecimal amountLimit,
        BigDecimal amountSpent,     // calculated at query time
        BigDecimal remaining,       // amountLimit - amountSpent
        double percentageUsed,
        LocalDate month,
        LocalDateTime createdAt
    ) {}
}
