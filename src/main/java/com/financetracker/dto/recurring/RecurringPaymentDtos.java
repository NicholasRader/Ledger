package com.financetracker.dto.recurring;

import com.financetracker.domain.entity.RecurringPayment.Frequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class RecurringPaymentDtos {

    public record CreateRecurringPaymentRequest(
        @NotNull Long accountId,
        Long categoryId,

        @NotBlank(message = "Name is required")
        String name,

        @NotNull @Positive
        BigDecimal amount,

        String currency,

        @NotNull Frequency frequency,

        @NotNull(message = "Next due date is required")
        LocalDate nextDueDate
    ) {}

    public record UpdateRecurringPaymentRequest(
        String name,
        BigDecimal amount,
        Frequency frequency,
        LocalDate nextDueDate,
        Boolean active
    ) {}

    public record RecurringPaymentResponse(
        Long id,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        String name,
        BigDecimal amount,
        String currency,
        Frequency frequency,
        LocalDate nextDueDate,
        boolean active,
        LocalDateTime createdAt
    ) {}
}
