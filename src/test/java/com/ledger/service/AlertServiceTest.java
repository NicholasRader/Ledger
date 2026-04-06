package com.ledger.service;

import com.ledger.domain.entity.Alert;
import com.ledger.domain.entity.Alert.AlertType;
import com.ledger.domain.entity.User;
import com.ledger.domain.repository.AlertRepository;
import com.ledger.dto.alert.AlertDtos.*;
import com.ledger.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService Unit Tests")
class AlertServiceTest {

    @Mock private AlertRepository alertRepository;
    @InjectMocks private AlertService alertService;

    private User testUser;
    private Alert budgetAlert;
    private Alert anomalyAlert;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L).email("test@example.com").fullName("Test").role("ROLE_USER")
            .build();

        budgetAlert = Alert.builder()
            .id(1L).user(testUser)
            .alertType(AlertType.BUDGET_BREACH)
            .message("Budget exceeded for Food & Dining")
            .build();
        ReflectionTestUtils.setField(budgetAlert, "isRead", false);
        ReflectionTestUtils.setField(budgetAlert, "createdAt", LocalDateTime.now());

        anomalyAlert = Alert.builder()
            .id(2L).user(testUser)
            .alertType(AlertType.ANOMALY)
            .message("Unusual spending detected in Transportation")
            .build();
        ReflectionTestUtils.setField(anomalyAlert, "isRead", false);
        ReflectionTestUtils.setField(anomalyAlert, "createdAt", LocalDateTime.now());
    }

    @Nested
    @DisplayName("getAlerts()")
    class GetAlerts {

        @Test
        @DisplayName("should return all alerts when unreadOnly is false")
        void getAlerts_allAlerts() {
            PageImpl<Alert> page = new PageImpl<>(List.of(budgetAlert, anomalyAlert),
                PageRequest.of(0, 20), 2);

            when(alertRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(page);
            when(alertRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(2L);

            AlertSummaryResponse response = alertService.getAlerts(1L, 0, 20, false);

            assertThat(response.alerts()).hasSize(2);
            assertThat(response.unreadCount()).isEqualTo(2);
            assertThat(response.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return only unread alerts when unreadOnly is true")
        void getAlerts_unreadOnly() {
            PageImpl<Alert> page = new PageImpl<>(List.of(budgetAlert),
                PageRequest.of(0, 20), 1);

            when(alertRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(page);
            when(alertRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(1L);

            AlertSummaryResponse response = alertService.getAlerts(1L, 0, 20, true);

            assertThat(response.alerts()).hasSize(1);
            assertThat(response.alerts().get(0).alertType()).isEqualTo(AlertType.BUDGET_BREACH);
        }

        @Test
        @DisplayName("should return correct unread count")
        void getAlerts_correctUnreadCount() {
            PageImpl<Alert> page = new PageImpl<>(List.of(),
                PageRequest.of(0, 20), 0);

            when(alertRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(page);
            when(alertRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(5L);

            AlertSummaryResponse response = alertService.getAlerts(1L, 0, 20, false);

            assertThat(response.unreadCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("should mark alert as read and return updated response")
        void markAsRead_success() {
            when(alertRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(budgetAlert));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AlertResponse response = alertService.markAsRead(1L, 1L);

            assertThat(budgetAlert.isRead()).isTrue();
            assertThat(response.isRead()).isTrue();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when alert not found")
        void markAsRead_notFound() {
            when(alertRepository.findByIdAndUserId(99L, 1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.markAsRead(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Alert not found");
        }
    }

    @Nested
    @DisplayName("markAllAsRead()")
    class MarkAllAsRead {

        @Test
        @DisplayName("should mark all alerts as read and return count")
        void markAllAsRead_returnsCount() {
            when(alertRepository.markAllReadForUser(1L)).thenReturn(3);

            int count = alertService.markAllAsRead(1L);

            assertThat(count).isEqualTo(3);
            verify(alertRepository).markAllReadForUser(1L);
        }

        @Test
        @DisplayName("should return 0 when no unread alerts exist")
        void markAllAsRead_noneToMark() {
            when(alertRepository.markAllReadForUser(1L)).thenReturn(0);

            int count = alertService.markAllAsRead(1L);

            assertThat(count).isEqualTo(0);
        }
    }
}
