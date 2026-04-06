package com.ledger.controller;

import com.ledger.domain.entity.User;
import com.ledger.dto.analytics.AnalyticsDtos.*;
import com.ledger.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics", description = "Financial intelligence and reporting endpoints")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/cashflow")
    @Operation(summary = "Get income vs expenses for a given month",
               description = "Defaults to current month if no month param provided")
    public ResponseEntity<CashFlowResponse> getCashFlow(
        @AuthenticationPrincipal User user,
        @RequestParam(required = false) String month  // format: 2025-03
    ) {
        YearMonth targetMonth = month != null ? YearMonth.parse(month) : YearMonth.now();
        return ResponseEntity.ok(analyticsService.getCashFlow(user.getId(), targetMonth));
    }

    @GetMapping("/networth")
    @Operation(summary = "Get total net worth across all accounts with per-account breakdown")
    public ResponseEntity<NetWorthResponse> getNetWorth(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analyticsService.getNetWorth(user.getId()));
    }

    @GetMapping("/burnrate")
    @Operation(summary = "Get daily average spending for a specific category over a date range")
    public ResponseEntity<BurnRateResponse> getBurnRate(
        @AuthenticationPrincipal User user,
        @RequestParam Long categoryId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(analyticsService.getBurnRate(user.getId(), categoryId, start, end));
    }

    @GetMapping("/trends")
    @Operation(summary = "Get month-over-month income and expense trends",
               description = "Returns data points for the last N months (default: 6)")
    public ResponseEntity<MonthlyTrendResponse> getMonthlyTrends(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "6") int months
    ) {
        return ResponseEntity.ok(analyticsService.getMonthlyTrends(user.getId(), months));
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Get spending breakdown by category for a date range")
    public ResponseEntity<SpendingBreakdownResponse> getSpendingBreakdown(
        @AuthenticationPrincipal User user,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(analyticsService.getSpendingBreakdown(user.getId(), start, end));
    }
}
