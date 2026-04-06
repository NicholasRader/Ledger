package com.ledger.controller;

import com.ledger.domain.entity.User;
import com.ledger.dto.alert.AlertDtos.*;
import com.ledger.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Alerts", description = "Budget breach and anomaly alert management")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "Get paginated alerts. Use ?unreadOnly=true to filter unread alerts")
    public ResponseEntity<AlertSummaryResponse> getAlerts(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return ResponseEntity.ok(alertService.getAlerts(user.getId(), page, size, unreadOnly));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a specific alert as read")
    public ResponseEntity<AlertResponse> markAsRead(
        @PathVariable Long id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(alertService.markAsRead(id, user.getId()));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all alerts as read for the authenticated user")
    public ResponseEntity<Map<String, Integer>> markAllRead(@AuthenticationPrincipal User user) {
        int count = alertService.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("markedAsRead", count));
    }
}
