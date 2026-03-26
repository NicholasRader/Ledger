package com.financetracker.service;

import com.financetracker.domain.entity.Budget;
import com.financetracker.domain.entity.Category;
import com.financetracker.domain.entity.User;
import com.financetracker.domain.repository.BudgetRepository;
import com.financetracker.domain.repository.CategoryRepository;
import com.financetracker.domain.repository.TransactionRepository;
import com.financetracker.dto.budget.BudgetDtos.*;
import com.financetracker.dto.analytics.CategorySpendDto;
import com.financetracker.exception.DuplicateResourceException;
import com.financetracker.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.financetracker.config.CacheConfig.CACHE_BUDGET_SUMMARY;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CACHE_BUDGET_SUMMARY, key = "#user.id")
    public BudgetResponse createBudget(CreateBudgetRequest request, User user) {
        // Normalize to first day of month
        LocalDate month = request.month().withDayOfMonth(1);

        budgetRepository.findByUserIdAndCategoryIdAndMonth(user.getId(), request.categoryId(), month)
            .ifPresent(b -> { throw new DuplicateResourceException(
                "Budget already exists for this category and month"); });

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));

        Budget budget = Budget.builder()
            .user(user)
            .category(category)
            .amountLimit(request.amountLimit())
            .month(month)
            .build();

        Budget saved = budgetRepository.save(budget);
        return toResponse(saved, BigDecimal.ZERO);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Cacheable(value = CACHE_BUDGET_SUMMARY, key = "#userId + ':' + #month")
    public List<BudgetResponse> getBudgetsForMonth(Long userId, LocalDate month) {
        LocalDate normalizedMonth = month.withDayOfMonth(1);
        LocalDate endOfMonth = normalizedMonth.withDayOfMonth(normalizedMonth.lengthOfMonth());

        List<Budget> budgets = budgetRepository.findByUserIdAndMonth(userId, normalizedMonth);

        // Get actual spend per category for this month in a single DB query
        List<CategorySpendDto> spends = transactionRepository
            .sumExpensesByCategoryAndDateRange(userId, normalizedMonth, endOfMonth);

        Map<Long, BigDecimal> spendByCategoryId = spends.stream()
            .collect(Collectors.toMap(CategorySpendDto::getCategoryId, CategorySpendDto::getTotalAmount));

        return budgets.stream()
            .map(b -> toResponse(b, spendByCategoryId.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO)))
            .toList();
    }

    public BudgetResponse getBudgetById(Long budgetId, Long userId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found: " + budgetId));

        LocalDate start = budget.getMonth().withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<CategorySpendDto> spends = transactionRepository
            .sumExpensesByCategoryAndDateRange(userId, start, end);

        BigDecimal spent = spends.stream()
            .filter(s -> s.getCategoryId().equals(budget.getCategory().getId()))
            .map(CategorySpendDto::getTotalAmount)
            .findFirst()
            .orElse(BigDecimal.ZERO);

        return toResponse(budget, spent);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CACHE_BUDGET_SUMMARY, key = "#userId")
    public BudgetResponse updateBudget(Long budgetId, Long userId, UpdateBudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found: " + budgetId));

        if (request.amountLimit() != null) {
            budget.setAmountLimit(request.amountLimit());
        }

        return toResponse(budgetRepository.save(budget), BigDecimal.ZERO);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CACHE_BUDGET_SUMMARY, key = "#userId")
    public void deleteBudget(Long budgetId, Long userId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found: " + budgetId));
        budgetRepository.delete(budget);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    public BudgetResponse toResponse(Budget budget, BigDecimal amountSpent) {
        BigDecimal limit = budget.getAmountLimit();
        BigDecimal remaining = limit.subtract(amountSpent);
        double percentage = limit.compareTo(BigDecimal.ZERO) == 0 ? 0.0
            : amountSpent.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() * 100;

        return new BudgetResponse(
            budget.getId(),
            budget.getCategory().getId(),
            budget.getCategory().getName(),
            limit,
            amountSpent,
            remaining,
            Math.round(percentage * 100.0) / 100.0,
            budget.getMonth(),
            budget.getCreatedAt()
        );
    }
}
