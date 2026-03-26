package com.financetracker.controller;

import com.financetracker.domain.entity.User;
import com.financetracker.dto.budget.BudgetDtos.*;
import com.financetracker.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Budgets", description = "Manage monthly envelope budgets per category")
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    @Operation(summary = "Create a budget envelope for a category and month")
    public ResponseEntity<BudgetResponse> create(
        @Valid @RequestBody CreateBudgetRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(budgetService.createBudget(request, user));
    }

    @GetMapping
    @Operation(summary = "Get all budgets for a given month with real-time spend data")
    public ResponseEntity<List<BudgetResponse>> getByMonth(
        @AuthenticationPrincipal User user,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month
    ) {
        LocalDate targetMonth = month != null ? month : LocalDate.now().withDayOfMonth(1);
        return ResponseEntity.ok(budgetService.getBudgetsForMonth(user.getId(), targetMonth));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single budget by ID")
    public ResponseEntity<BudgetResponse> getById(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(budgetService.getBudgetById(id, user.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a budget's spending limit")
    public ResponseEntity<BudgetResponse> update(
        @PathVariable Long id,
        @RequestBody UpdateBudgetRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(budgetService.updateBudget(id, user.getId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a budget")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        budgetService.deleteBudget(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
