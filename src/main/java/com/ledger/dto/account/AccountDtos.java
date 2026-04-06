package com.ledger.dto.account;

import com.ledger.domain.entity.Account.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AccountDtos {

    public record CreateAccountRequest(
        @NotBlank(message = "Account name is required")
        String name,

        @NotNull(message = "Account type is required")
        AccountType accountType,

        @PositiveOrZero(message = "Initial balance must be zero or positive")
        BigDecimal initialBalance,

        String currency
    ) {}

    public record UpdateAccountRequest(
        String name,
        AccountType accountType,
        String currency
    ) {}

    public record AccountResponse(
        Long id,
        String name,
        AccountType accountType,
        BigDecimal balance,
        String currency,
        LocalDateTime createdAt
    ) {}
}
