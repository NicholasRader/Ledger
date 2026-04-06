package com.ledger.dto.alert;

import com.ledger.domain.entity.Alert.AlertType;

import java.time.LocalDateTime;
import java.util.List;

public class AlertDtos {

    public record AlertResponse(
        Long id,
        AlertType alertType,
        String message,
        boolean isRead,
        LocalDateTime createdAt
    ) {}

    public record AlertSummaryResponse(
        List<AlertResponse> alerts,
        long unreadCount,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}
}
