package com.ledger.service;

import com.ledger.domain.entity.*;
import com.ledger.domain.repository.BudgetRepository;
import com.ledger.domain.repository.CategoryRepository;
import com.ledger.domain.repository.TransactionRepository;
import com.ledger.dto.analytics.CategorySpendDto;
import com.ledger.dto.budget.BudgetDtos.*;
import com.ledger.exception.DuplicateResourceException;
import com.ledger.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetService Unit Tests")
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @InjectMocks private BudgetService budgetService;

    private User testUser;
    private Category testCategory;
    private Budget testBudget;
    private final LocalDate march2026 = LocalDate.of(2026, 3, 1);

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L).email("test@example.com").fullName("Test").role("ROLE_USER")
            .build();

        testCategory = Category.builder()
            .id(1L).name("Food & Dining").icon("utensils").color("#FF6B6B").isDefault(true)
            .build();

        testBudget = Budget.builder()
            .id(1L).user(testUser).category(testCategory)
            .amountLimit(new BigDecimal("400.00")).month(march2026)
            .build();
        ReflectionTestUtils.setField(testBudget, "createdAt", LocalDateTime.now());
    }

    @Nested
    @DisplayName("createBudget()")
    class CreateBudget {

        @Test
        @DisplayName("should create budget and return response with zero spend")
        void createBudget_success() {
            CreateBudgetRequest request = new CreateBudgetRequest(1L, new BigDecimal("400.00"), march2026);

            when(budgetRepository.findByUserIdAndCategoryIdAndMonth(1L, 1L, march2026))
                .thenReturn(Optional.empty());
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
            when(budgetRepository.save(any())).thenAnswer(inv -> {
                Budget b = inv.getArgument(0);
                ReflectionTestUtils.setField(b, "id", 1L);
                ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now());
                return b;
            });

            BudgetResponse response = budgetService.createBudget(request, testUser);

            assertThat(response.amountLimit()).isEqualByComparingTo("400.00");
            assertThat(response.amountSpent()).isEqualByComparingTo("0.00");
            assertThat(response.percentageUsed()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should normalize month to first day")
        void createBudget_normalizesMonthToFirstDay() {
            // Pass mid-month date - should be normalized to first day
            LocalDate midMonth = LocalDate.of(2026, 3, 15);
            CreateBudgetRequest request = new CreateBudgetRequest(1L, new BigDecimal("300.00"), midMonth);

            when(budgetRepository.findByUserIdAndCategoryIdAndMonth(1L, 1L, march2026))
                .thenReturn(Optional.empty());
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
            when(budgetRepository.save(any())).thenAnswer(inv -> {
                Budget b = inv.getArgument(0);
                ReflectionTestUtils.setField(b, "id", 1L);
                ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now());
                return b;
            });

            BudgetResponse response = budgetService.createBudget(request, testUser);

            assertThat(response.month()).isEqualTo(march2026);
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when budget already exists")
        void createBudget_duplicate_throws() {
            CreateBudgetRequest request = new CreateBudgetRequest(1L, new BigDecimal("400.00"), march2026);

            when(budgetRepository.findByUserIdAndCategoryIdAndMonth(1L, 1L, march2026))
                .thenReturn(Optional.of(testBudget));

            assertThatThrownBy(() -> budgetService.createBudget(request, testUser))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Budget already exists");

            verify(budgetRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when category not found")
        void createBudget_categoryNotFound_throws() {
            CreateBudgetRequest request = new CreateBudgetRequest(99L, new BigDecimal("400.00"), march2026);

            when(budgetRepository.findByUserIdAndCategoryIdAndMonth(1L, 99L, march2026))
                .thenReturn(Optional.empty());
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.createBudget(request, testUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
        }
    }

    @Nested
    @DisplayName("getBudgetsForMonth()")
    class GetBudgetsForMonth {

        @Test
        @DisplayName("should return budgets with correct spend amounts")
        void getBudgetsForMonth_withSpend() {
            CategorySpendDto spendDto = new CategorySpendDto(1L, "Food & Dining", new BigDecimal("250.00"));

            when(budgetRepository.findByUserIdAndMonth(1L, march2026))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), eq(march2026), any()))
                .thenReturn(List.of(spendDto));

            List<BudgetResponse> result = budgetService.getBudgetsForMonth(1L, march2026);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).amountSpent()).isEqualByComparingTo("250.00");
            assertThat(result.get(0).remaining()).isEqualByComparingTo("150.00");
            assertThat(result.get(0).percentageUsed()).isEqualTo(62.5);
        }

        @Test
        @DisplayName("should show 0 spend when no transactions exist")
        void getBudgetsForMonth_noSpend() {
            when(budgetRepository.findByUserIdAndMonth(1L, march2026))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), eq(march2026), any()))
                .thenReturn(List.of());

            List<BudgetResponse> result = budgetService.getBudgetsForMonth(1L, march2026);

            assertThat(result.get(0).amountSpent()).isEqualByComparingTo("0.00");
            assertThat(result.get(0).percentageUsed()).isEqualTo(0.0);
            assertThat(result.get(0).remaining()).isEqualByComparingTo("400.00");
        }

        @Test
        @DisplayName("should show 100% when budget is exactly met")
        void getBudgetsForMonth_exactlyAtLimit() {
            CategorySpendDto spendDto = new CategorySpendDto(1L, "Food & Dining", new BigDecimal("400.00"));

            when(budgetRepository.findByUserIdAndMonth(1L, march2026))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), eq(march2026), any()))
                .thenReturn(List.of(spendDto));

            List<BudgetResponse> result = budgetService.getBudgetsForMonth(1L, march2026);

            assertThat(result.get(0).percentageUsed()).isEqualTo(100.0);
            assertThat(result.get(0).remaining()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("should show over 100% when budget is exceeded")
        void getBudgetsForMonth_exceeded() {
            CategorySpendDto spendDto = new CategorySpendDto(1L, "Food & Dining", new BigDecimal("500.00"));

            when(budgetRepository.findByUserIdAndMonth(1L, march2026))
                .thenReturn(List.of(testBudget));
            when(transactionRepository.sumExpensesByCategoryAndDateRange(
                eq(1L), eq(march2026), any()))
                .thenReturn(List.of(spendDto));

            List<BudgetResponse> result = budgetService.getBudgetsForMonth(1L, march2026);

            assertThat(result.get(0).percentageUsed()).isGreaterThan(100.0);
            assertThat(result.get(0).remaining()).isNegative();
        }
    }

    @Nested
    @DisplayName("updateBudget()")
    class UpdateBudget {

        @Test
        @DisplayName("should update amount limit")
        void updateBudget_updatesLimit() {
            UpdateBudgetRequest request = new UpdateBudgetRequest(new BigDecimal("600.00"));

            when(budgetRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(testBudget));
            when(budgetRepository.save(any())).thenReturn(testBudget);

            budgetService.updateBudget(1L, 1L, request);

            assertThat(testBudget.getAmountLimit()).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("should throw when budget not found")
        void updateBudget_notFound() {
            when(budgetRepository.findByIdAndUserId(99L, 1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.updateBudget(
                99L, 1L, new UpdateBudgetRequest(new BigDecimal("100.00"))))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteBudget()")
    class DeleteBudget {

        @Test
        @DisplayName("should delete budget when found")
        void deleteBudget_success() {
            when(budgetRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(testBudget));

            budgetService.deleteBudget(1L, 1L);

            verify(budgetRepository).delete(testBudget);
        }

        @Test
        @DisplayName("should throw when budget not found")
        void deleteBudget_notFound() {
            when(budgetRepository.findByIdAndUserId(99L, 1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.deleteBudget(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(budgetRepository, never()).delete(any());
        }
    }
}
