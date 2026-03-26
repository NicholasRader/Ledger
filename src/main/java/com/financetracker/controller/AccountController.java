package com.financetracker.controller;

import com.financetracker.domain.entity.User;
import com.financetracker.dto.account.AccountDtos.*;
import com.financetracker.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Accounts", description = "Manage financial accounts")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<AccountResponse> create(
        @Valid @RequestBody CreateAccountRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(accountService.createAccount(request, user));
    }

    @GetMapping
    @Operation(summary = "Get all accounts for the authenticated user")
    public ResponseEntity<List<AccountResponse>> getAll(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(accountService.getAccountsForUser(user.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific account by ID")
    public ResponseEntity<AccountResponse> getById(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(accountService.getAccountById(id, user.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public ResponseEntity<AccountResponse> update(
        @PathVariable Long id,
        @RequestBody UpdateAccountRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(accountService.updateAccount(id, user.getId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an account and all its transactions")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        accountService.deleteAccount(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
