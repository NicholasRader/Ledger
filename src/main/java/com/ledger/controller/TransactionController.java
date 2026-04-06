package com.ledger.controller;

import com.ledger.domain.entity.User;
import com.ledger.dto.transaction.TransactionDtos.*;
import com.ledger.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Record and manage financial transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public ResponseEntity<TransactionResponse> create(
        @Valid @RequestBody CreateTransactionRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transactionService.createTransaction(request, user.getId()));
    }

    @GetMapping
    @Operation(summary = "Get paginated transaction history")
    public ResponseEntity<PagedTransactionResponse> getAll(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(transactionService.getTransactions(user.getId(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific transaction")
    public ResponseEntity<TransactionResponse> getById(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(transactionService.getTransactionById(id, user.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a transaction")
    public ResponseEntity<TransactionResponse> update(
        @PathVariable Long id,
        @RequestBody UpdateTransactionRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, user.getId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        transactionService.deleteTransaction(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
