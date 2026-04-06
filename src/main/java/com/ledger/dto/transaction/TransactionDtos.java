package com.ledger.dto.transaction;

import com.ledger.domain.entity.Transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TransactionDtos {

    public record CreateTransactionRequest(
        @NotNull(message = "Account ID is required")
        Long accountId,

        Long categoryId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        String currency,

        @NotNull(message = "Transaction type is required")
        TransactionType type,

        String description,
        String merchant,

        @NotNull(message = "Transaction date is required")
        LocalDate transactionDate
    ) {}

    public record UpdateTransactionRequest(
        Long categoryId,
        BigDecimal amount,
        TransactionType type,
        String description,
        String merchant,
        LocalDate transactionDate
    ) {}

    public record TransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        String currency,
        TransactionType type,
        String description,
        String merchant,
        LocalDate transactionDate,
        LocalDateTime createdAt
    ) {}

    public record PagedTransactionResponse(
        java.util.List<TransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}
}
