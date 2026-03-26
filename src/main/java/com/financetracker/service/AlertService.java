package com.financetracker.service;

import com.financetracker.domain.entity.Alert;
import com.financetracker.domain.repository.AlertRepository;
import com.financetracker.dto.alert.AlertDtos.*;
import com.financetracker.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertSummaryResponse getAlerts(Long userId, int page, int size, boolean unreadOnly) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<Alert> result = unreadOnly
            ? alertRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
            : alertRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        long unreadCount = alertRepository.countByUserIdAndIsReadFalse(userId);

        List<AlertResponse> alerts = result.getContent().stream().map(this::toResponse).toList();

        return new AlertSummaryResponse(
            alerts, unreadCount,
            result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional
    public AlertResponse markAsRead(Long alertId, Long userId) {
        Alert alert = alertRepository.findByIdAndUserId(alertId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));
        alert.setRead(true);
        return toResponse(alertRepository.save(alert));
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return alertRepository.markAllReadForUser(userId);
    }

    public AlertResponse toResponse(Alert a) {
        return new AlertResponse(
            a.getId(), a.getAlertType(), a.getMessage(), a.isRead(), a.getCreatedAt()
        );
    }
}
