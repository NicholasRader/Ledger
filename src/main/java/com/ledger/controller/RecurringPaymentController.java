package com.ledger.controller;

import com.ledger.domain.entity.User;
import com.ledger.dto.recurring.RecurringPaymentDtos.*;
import com.ledger.service.RecurringPaymentService;
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
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recurring Payments", description = "Manage subscriptions and recurring expenses")
public class RecurringPaymentController {

    private final RecurringPaymentService recurringPaymentService;

    @PostMapping
    @Operation(summary = "Create a recurring payment")
    public ResponseEntity<RecurringPaymentResponse> create(
        @Valid @RequestBody CreateRecurringPaymentRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(recurringPaymentService.create(request, user));
    }

    @GetMapping
    @Operation(summary = "Get all recurring payments for the authenticated user")
    public ResponseEntity<List<RecurringPaymentResponse>> getAll(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recurringPaymentService.getAllForUser(user.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a recurring payment by ID")
    public ResponseEntity<RecurringPaymentResponse> getById(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(recurringPaymentService.getById(id, user.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a recurring payment")
    public ResponseEntity<RecurringPaymentResponse> update(
        @PathVariable Long id,
        @RequestBody UpdateRecurringPaymentRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(recurringPaymentService.update(id, user.getId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a recurring payment")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        recurringPaymentService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
