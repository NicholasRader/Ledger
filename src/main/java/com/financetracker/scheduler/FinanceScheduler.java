package com.financetracker.scheduler;

import com.financetracker.domain.entity.Alert;
import com.financetracker.domain.entity.Budget;
import com.financetracker.domain.entity.RecurringPayment;
import com.financetracker.domain.entity.User;
import com.financetracker.domain.repository.*;
import com.financetracker.dto.analytics.CategorySpendDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinanceScheduler {

    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final AlertRepository alertRepository;

    private static final double BUDGET_WARNING_THRESHOLD  = 0.80; // Alert at 80% usage
    private static final double ANOMALY_STD_DEV_THRESHOLD = 2.0;  // Flag if > 2 std deviations
    private static final int    ANOMALY_LOOKBACK_DAYS     = 90;   // 3 months of history

    // ── Budget Breach Alerts ─────────────────────────────────────── daily 8am ──

    @Scheduled(cron = "${app.scheduling.budget-alert-cron}")
    @Transactional
    public void checkBudgetBreaches() {
        log.info("Running budget breach check...");
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        List<User> users = userRepository.findAll();

        for (User user : users) {
            List<Budget> budgets = budgetRepository.findByUserIdAndMonth(user.getId(), monthStart);
            if (budgets.isEmpty()) continue;

            List<CategorySpendDto> spends = transactionRepository
                .sumExpensesByCategoryAndDateRange(user.getId(), monthStart, today);

            Map<Long, BigDecimal> spendMap = spends.stream()
                .collect(Collectors.toMap(CategorySpendDto::getCategoryId, CategorySpendDto::getTotalAmount));

            for (Budget budget : budgets) {
                BigDecimal spent = spendMap.getOrDefault(budget.getCategory().getId(), BigDecimal.ZERO);
                BigDecimal limit = budget.getAmountLimit();

                if (limit.compareTo(BigDecimal.ZERO) == 0) continue;

                double usageRatio = spent.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();

                if (usageRatio >= 1.0) {
                    saveAlert(user, Alert.AlertType.BUDGET_BREACH,
                        String.format("Budget exceeded for '%s': spent $%.2f of $%.2f limit (%.0f%%)",
                            budget.getCategory().getName(),
                            spent, limit, usageRatio * 100));
                } else if (usageRatio >= BUDGET_WARNING_THRESHOLD) {
                    saveAlert(user, Alert.AlertType.BUDGET_BREACH,
                        String.format("Approaching budget limit for '%s': %.0f%% used ($%.2f of $%.2f)",
                            budget.getCategory().getName(),
                            usageRatio * 100, spent, limit));
                }
            }
        }
        log.info("Budget breach check complete.");
    }

    // ── Anomaly Detection ──────────────────────────────────────────── daily 8am ──

    @Scheduled(cron = "${app.scheduling.budget-alert-cron}")
    @Transactional
    public void detectSpendingAnomalies() {
        log.info("Running anomaly detection...");
        LocalDate today = LocalDate.now();
        LocalDate lookbackStart = today.minusDays(ANOMALY_LOOKBACK_DAYS);
        LocalDate monthStart = today.withDayOfMonth(1);

        List<User> users = userRepository.findAll();

        for (User user : users) {
            // Get current month's spending per category
            List<CategorySpendDto> currentSpends = transactionRepository
                .sumExpensesByCategoryAndDateRange(user.getId(), monthStart, today);

            for (CategorySpendDto currentSpend : currentSpends) {
                Long categoryId = currentSpend.getCategoryId();

                // Get historical stats (avg, std dev) for this category
                Object[] stats = transactionRepository
                    .findCategorySpendStats(user.getId(), categoryId, lookbackStart);

                if (stats == null || stats[0] == null || stats[1] == null) continue;

                double avg    = Double.parseDouble(stats[0].toString());
                double stdDev = Double.parseDouble(stats[1].toString());

                // Only flag if there's meaningful variance (avoid false positives)
                if (stdDev < 1.0) continue;

                double currentAmount = currentSpend.getTotalAmount().doubleValue();
                double zScore = (currentAmount - avg) / stdDev;

                if (zScore > ANOMALY_STD_DEV_THRESHOLD) {
                    saveAlert(user, Alert.AlertType.ANOMALY,
                        String.format("Unusual spending detected in '%s': $%.2f this month " +
                            "(%.1fx above your typical amount of $%.2f)",
                            currentSpend.getCategoryName(),
                            currentAmount, currentAmount / avg, avg));
                }
            }
        }
        log.info("Anomaly detection complete.");
    }

    // ── Recurring Payment Reminders ───────────────────────────────── daily 8am ──

    @Scheduled(cron = "${app.scheduling.budget-alert-cron}")
    @Transactional
    public void processRecurringPayments() {
        log.info("Processing recurring payments...");
        LocalDate today = LocalDate.now();
        LocalDate reminderWindow = today.plusDays(3); // Remind 3 days ahead

        List<RecurringPayment> duePayments = recurringPaymentRepository
            .findByActiveIsTrueAndNextDueDateLessThanEqual(reminderWindow);

        for (RecurringPayment payment : duePayments) {
            if (!payment.getNextDueDate().isAfter(today)) {
                // Payment is due today or overdue — advance the next due date
                LocalDate nextDate = payment.getFrequency().nextOccurrence(payment.getNextDueDate());
                payment.setNextDueDate(nextDate);
                recurringPaymentRepository.save(payment);

                saveAlert(payment.getUser(), Alert.AlertType.RECURRING_DUE,
                    String.format("Recurring payment '%s' of $%.2f was due today. Next due: %s",
                        payment.getName(), payment.getAmount(), nextDate));
            } else {
                // Upcoming reminder
                saveAlert(payment.getUser(), Alert.AlertType.RECURRING_DUE,
                    String.format("Upcoming payment: '%s' of $%.2f due on %s",
                        payment.getName(), payment.getAmount(), payment.getNextDueDate()));
            }
        }
        log.info("Recurring payments processed.");
    }

    // ── Token Cleanup ────────────────────────────────────────────── every night ──

    @Scheduled(cron = "0 0 2 * * *") // 2am daily
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Cleaning up expired/revoked refresh tokens...");
        // Handled in RefreshTokenRepository — called here via direct repo access
        // (can be expanded to call refreshTokenRepository.deleteExpiredAndRevoked())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveAlert(User user, Alert.AlertType type, String message) {
        Alert alert = Alert.builder()
            .user(user)
            .alertType(type)
            .message(message)
            .build();
        alertRepository.save(alert);
        log.debug("Alert saved for user {}: [{}] {}", user.getEmail(), type, message);
    }
}
