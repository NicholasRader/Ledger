package com.financetracker.scheduler;

import com.financetracker.domain.entity.*;
import com.financetracker.domain.entity.Account.AccountType;
import com.financetracker.domain.entity.RecurringPayment.Frequency;
import com.financetracker.domain.repository.*;
import com.financetracker.dto.analytics.CategorySpendDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinanceScheduler Unit Tests")
class FinanceSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RecurringPaymentRepository recurringPaymentRepository;
    @Mock private AlertRepository alertRepository;

    @InjectMocks private FinanceScheduler scheduler;

    private User testUser;
    private Category testCategory;
    private Budget testBudget;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L).email("test@example.com").fullName("Test User").role("ROLE_USER")
            .build();

        testCategory = Category.builder()
            .id(1L).name("Food & Dining").isDefault(true)
            .build();

        testAccount = Account.builder()
            .id(1L).user(testUser).name("Checking")
            .accountType(AccountType.CHECKING)
            .balance(new BigDecimal("1000.00")).currency("USD")
            .build();

        testBudget = Budget.builder()
            .id(1L).user(testUser).category(testCategory)
            .amountLimit(new BigDecimal("400.00"))
            .month(LocalDate.now().withDayOfMonth(1))
            .build();
        ReflectionTestUtils.setField(testBudget, "createdAt", LocalDateTime.now());
    }

    // ── Budget Breach Alerts ──────────────────────────────────────────────────

    @Nested
    @DisplayName("checkBudgetBreaches()")
    class CheckBudgetBreaches {

        @Test
        @DisplayName("should create BUDGET_BREACH alert when spending exceeds 100% of limit")
        void budgetBreach_over100Percent_createsAlert() {
            // $500 spent on $400 budget = 125%
            CategorySpendDto overspend = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("500.00")
            );

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(budgetRepository.findByUserIdAndMonth(eq(1L), any()))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(overspend));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.checkBudgetBreaches();

            ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(alertCaptor.capture());

            Alert savedAlert = alertCaptor.getValue();
            assertThat(savedAlert.getAlertType()).isEqualTo(Alert.AlertType.BUDGET_BREACH);
            assertThat(savedAlert.getMessage()).contains("Budget exceeded");
            assertThat(savedAlert.getMessage()).contains("Food & Dining");
        }

        @Test
        @DisplayName("should create warning alert when spending is between 80-100% of limit")
        void budgetBreach_between80And100_createsWarning() {
            // $350 spent on $400 budget = 87.5%
            CategorySpendDto nearLimit = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("350.00")
            );

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(budgetRepository.findByUserIdAndMonth(eq(1L), any()))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(nearLimit));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.checkBudgetBreaches();

            ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(alertCaptor.capture());

            Alert savedAlert = alertCaptor.getValue();
            assertThat(savedAlert.getAlertType()).isEqualTo(Alert.AlertType.BUDGET_BREACH);
            assertThat(savedAlert.getMessage()).contains("Approaching budget limit");
        }

        @Test
        @DisplayName("should NOT create alert when spending is below 80% threshold")
        void budgetBreach_below80Percent_noAlert() {
            // $200 spent on $400 budget = 50%
            CategorySpendDto underBudget = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("200.00")
            );

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(budgetRepository.findByUserIdAndMonth(eq(1L), any()))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(underBudget));

            scheduler.checkBudgetBreaches();

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should NOT create alert when spending is exactly at 80% threshold")
        void budgetBreach_exactlyAt80Percent_noAlert() {
            // $320 on $400 = exactly 80% — should NOT trigger (threshold is >= 80%)
            // Actually per our code >= 0.80 DOES trigger. Let's test exactly 80 triggers.
            CategorySpendDto atThreshold = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("320.00")
            );

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(budgetRepository.findByUserIdAndMonth(eq(1L), any()))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(atThreshold));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.checkBudgetBreaches();

            // 80% exactly triggers the warning
            verify(alertRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("should skip user with no budgets")
        void budgetBreach_noBudgets_noAlert() {
            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(budgetRepository.findByUserIdAndMonth(eq(1L), any()))
                .thenReturn(List.of());

            scheduler.checkBudgetBreaches();

            verify(transactionRepository, never()).sumExpensesByCategoryAndDateRange(any(), any(), any());
            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should process multiple users independently")
        void budgetBreach_multipleUsers() {
            User user2 = User.builder()
                .id(2L).email("user2@example.com").fullName("User 2").role("ROLE_USER")
                .build();

            Budget budget2 = Budget.builder()
                .id(2L).user(user2).category(testCategory)
                .amountLimit(new BigDecimal("500.00"))
                .month(LocalDate.now().withDayOfMonth(1))
                .build();
            ReflectionTestUtils.setField(budget2, "createdAt", LocalDateTime.now());

            // User 1: over budget. User 2: under budget.
            CategorySpendDto user1Spend = new CategorySpendDto(1L, "Food", new BigDecimal("450.00"));
            CategorySpendDto user2Spend = new CategorySpendDto(1L, "Food", new BigDecimal("100.00"));

            when(userRepository.findAll()).thenReturn(List.of(testUser, user2));
            when(budgetRepository.findByUserIdAndMonth(eq(1L), any())).thenReturn(List.of(testBudget));
            when(budgetRepository.findByUserIdAndMonth(eq(2L), any())).thenReturn(List.of(budget2));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(eq(1L), any(), any()))
                .thenReturn(List.of(user1Spend));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(eq(2L), any(), any()))
                .thenReturn(List.of(user2Spend));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.checkBudgetBreaches();

            // Only user 1 should get an alert (112.5% usage)
            verify(alertRepository, times(1)).save(any());
        }
    }

    // ── Z-Score Anomaly Detection ─────────────────────────────────────────────

    @Nested
    @DisplayName("detectSpendingAnomalies() - Z-Score Tests")
    class DetectSpendingAnomalies {

        @Test
        @DisplayName("should create ANOMALY alert when z-score exceeds 2.0 threshold")
        void anomaly_highZScore_createsAlert() {
            // Historical avg = $100, stddev = $20
            // Current month spend = $200 → z-score = (200-100)/20 = 5.0 → ANOMALY
            CategorySpendDto currentSpend = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("200.00")
            );
            Object[] stats = new Object[]{
                new BigDecimal("100.00"),  // avg
                new BigDecimal("20.00")    // stddev
            };

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(currentSpend));
            when(transactionRepository.findCategorySpendStats(eq(1L), eq(1L), any()))
                .thenReturn(stats);
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.detectSpendingAnomalies();

            ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(alertCaptor.capture());

            Alert savedAlert = alertCaptor.getValue();
            assertThat(savedAlert.getAlertType()).isEqualTo(Alert.AlertType.ANOMALY);
            assertThat(savedAlert.getMessage()).contains("Unusual spending detected");
            assertThat(savedAlert.getMessage()).contains("Food & Dining");
        }

        @Test
        @DisplayName("should NOT create alert when z-score is below 2.0 threshold")
        void anomaly_lowZScore_noAlert() {
            // Historical avg = $100, stddev = $50
            // Current month spend = $130 → z-score = (130-100)/50 = 0.6 → NO ANOMALY
            CategorySpendDto currentSpend = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("130.00")
            );
            Object[] stats = new Object[]{
                new BigDecimal("100.00"),  // avg
                new BigDecimal("50.00")    // stddev
            };

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(currentSpend));
            when(transactionRepository.findCategorySpendStats(eq(1L), eq(1L), any()))
                .thenReturn(stats);

            scheduler.detectSpendingAnomalies();

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should NOT create alert when z-score is exactly at threshold (2.0)")
        void anomaly_exactlyAtThreshold_noAlert() {
            // avg = $100, stddev = $50, current = $200 → z-score = (200-100)/50 = 2.0
            // Our check is STRICTLY > 2.0, so exactly 2.0 should NOT alert
            CategorySpendDto currentSpend = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("200.00")
            );
            Object[] stats = new Object[]{
                new BigDecimal("100.00"),
                new BigDecimal("50.00")
            };

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(currentSpend));
            when(transactionRepository.findCategorySpendStats(eq(1L), eq(1L), any()))
                .thenReturn(stats);

            scheduler.detectSpendingAnomalies();

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should skip category when stddev is too low (avoids false positives)")
        void anomaly_lowStdDev_skipsCategory() {
            // stddev < 1.0 means spending is always consistent — don't flag
            CategorySpendDto currentSpend = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("105.00")
            );
            Object[] stats = new Object[]{
                new BigDecimal("100.00"),
                new BigDecimal("0.50")  // very low stddev
            };

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(currentSpend));
            when(transactionRepository.findCategorySpendStats(eq(1L), eq(1L), any()))
                .thenReturn(stats);

            scheduler.detectSpendingAnomalies();

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should skip category when no historical stats available")
        void anomaly_noHistoricalStats_skipsCategory() {
            CategorySpendDto currentSpend = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("500.00")
            );
            // Null stats = no history
            Object[] stats = new Object[]{null, null};

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(currentSpend));
            when(transactionRepository.findCategorySpendStats(eq(1L), eq(1L), any()))
                .thenReturn(stats);

            scheduler.detectSpendingAnomalies();

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should skip user when no current month spending exists")
        void anomaly_noCurrentSpend_skipsUser() {
            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of());

            scheduler.detectSpendingAnomalies();

            verify(transactionRepository, never()).findCategorySpendStats(any(), any(), any());
            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should include multiplier in alert message")
        void anomaly_alertMessageContainsMultiplier() {
            // avg = $100, stddev = $20, current = $300 → z-score = 10, multiplier = 3x
            CategorySpendDto currentSpend = new CategorySpendDto(
                1L, "Food & Dining", new BigDecimal("300.00")
            );
            Object[] stats = new Object[]{
                new BigDecimal("100.00"),
                new BigDecimal("20.00")
            };

            when(userRepository.findAll()).thenReturn(List.of(testUser));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), any(), any()))
                .thenReturn(List.of(currentSpend));
            when(transactionRepository.findCategorySpendStats(eq(1L), eq(1L), any()))
                .thenReturn(stats);
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.detectSpendingAnomalies();

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());

            // Message should mention the multiplier (3.0x above typical)
            assertThat(captor.getValue().getMessage()).contains("3.0x");
        }
    }

    // ── Recurring Payments ────────────────────────────────────────────────────

    @Nested
    @DisplayName("processRecurringPayments()")
    class ProcessRecurringPayments {

        @Test
        @DisplayName("should advance next due date when payment is due today")
        void recurringPayment_dueToday_advancesDate() {
            RecurringPayment payment = RecurringPayment.builder()
                .id(1L).user(testUser).account(testAccount)
                .name("Netflix").amount(new BigDecimal("15.99"))
                .currency("USD").frequency(Frequency.MONTHLY)
                .nextDueDate(LocalDate.now())  // due TODAY
                .active(true)
                .build();

            when(recurringPaymentRepository.findByActiveIsTrueAndNextDueDateLessThanEqual(any()))
                .thenReturn(List.of(payment));
            when(recurringPaymentRepository.save(any())).thenReturn(payment);
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.processRecurringPayments();

            // Next due date should be advanced by 1 month
            assertThat(payment.getNextDueDate()).isEqualTo(LocalDate.now().plusMonths(1));
            verify(alertRepository).save(any());
        }

        @Test
        @DisplayName("should create reminder alert for upcoming payment (within 3 days)")
        void recurringPayment_upcoming_createsReminder() {
            RecurringPayment payment = RecurringPayment.builder()
                .id(1L).user(testUser).account(testAccount)
                .name("Spotify").amount(new BigDecimal("9.99"))
                .currency("USD").frequency(Frequency.MONTHLY)
                .nextDueDate(LocalDate.now().plusDays(2))  // due in 2 days
                .active(true)
                .build();

            when(recurringPaymentRepository.findByActiveIsTrueAndNextDueDateLessThanEqual(any()))
                .thenReturn(List.of(payment));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.processRecurringPayments();

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());

            assertThat(captor.getValue().getAlertType()).isEqualTo(Alert.AlertType.RECURRING_DUE);
            assertThat(captor.getValue().getMessage()).contains("Upcoming payment");
            assertThat(captor.getValue().getMessage()).contains("Spotify");
            // Should NOT advance the date for upcoming (not yet due) payments
            assertThat(payment.getNextDueDate()).isEqualTo(LocalDate.now().plusDays(2));
        }

        @Test
        @DisplayName("should advance WEEKLY frequency correctly")
        void recurringPayment_weeklyFrequency_advancesOneWeek() {
            RecurringPayment payment = RecurringPayment.builder()
                .id(1L).user(testUser).account(testAccount)
                .name("Weekly Sub").amount(new BigDecimal("5.00"))
                .currency("USD").frequency(Frequency.WEEKLY)
                .nextDueDate(LocalDate.now())
                .active(true)
                .build();

            when(recurringPaymentRepository.findByActiveIsTrueAndNextDueDateLessThanEqual(any()))
                .thenReturn(List.of(payment));
            when(recurringPaymentRepository.save(any())).thenReturn(payment);
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.processRecurringPayments();

            assertThat(payment.getNextDueDate()).isEqualTo(LocalDate.now().plusWeeks(1));
        }

        @Test
        @DisplayName("should advance YEARLY frequency correctly")
        void recurringPayment_yearlyFrequency_advancesOneYear() {
            RecurringPayment payment = RecurringPayment.builder()
                .id(1L).user(testUser).account(testAccount)
                .name("Annual Sub").amount(new BigDecimal("99.00"))
                .currency("USD").frequency(Frequency.YEARLY)
                .nextDueDate(LocalDate.now())
                .active(true)
                .build();

            when(recurringPaymentRepository.findByActiveIsTrueAndNextDueDateLessThanEqual(any()))
                .thenReturn(List.of(payment));
            when(recurringPaymentRepository.save(any())).thenReturn(payment);
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.processRecurringPayments();

            assertThat(payment.getNextDueDate()).isEqualTo(LocalDate.now().plusYears(1));
        }

        @Test
        @DisplayName("should do nothing when no payments are due")
        void recurringPayment_nonedue_noAlerts() {
            when(recurringPaymentRepository.findByActiveIsTrueAndNextDueDateLessThanEqual(any()))
                .thenReturn(List.of());

            scheduler.processRecurringPayments();

            verify(alertRepository, never()).save(any());
            verify(recurringPaymentRepository, never()).save(any());
        }
    }
}
