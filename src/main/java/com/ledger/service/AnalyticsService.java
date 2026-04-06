package com.ledger.service;

import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.TransactionRepository;
import com.ledger.dto.analytics.AnalyticsDtos.*;
import com.ledger.dto.analytics.CategorySpendDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.ledger.config.CacheConfig.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Cacheable(value = CACHE_CASH_FLOW, key = "#userId + ':' + #month")
    public CashFlowResponse getCashFlow(Long userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        BigDecimal income   = transactionRepository.sumIncomeByUserAndDateRange(userId, start, end);
        BigDecimal expenses = transactionRepository.sumExpensesByUserAndDateRange(userId, start, end);
        BigDecimal net      = income.subtract(expenses);

        return new CashFlowResponse(month.toString(), income, expenses, net);
    }

    @Cacheable(value = CACHE_NET_WORTH, key = "#userId")
    public NetWorthResponse getNetWorth(Long userId) {
        BigDecimal total = accountRepository.sumBalanceByUserId(userId);

        List<NetWorthResponse.AccountBalance> breakdown = accountRepository.findByUserId(userId)
            .stream()
            .map(a -> new NetWorthResponse.AccountBalance(
                a.getId(), a.getName(), a.getBalance(), a.getCurrency()))
            .toList();

        return new NetWorthResponse(total, breakdown);
    }

    public BurnRateResponse getBurnRate(Long userId, Long categoryId, LocalDate start, LocalDate end) {
        List<CategorySpendDto> spends = transactionRepository
            .sumExpensesByCategoryAndDateRange(userId, start, end);

        BigDecimal totalSpend = spends.stream()
            .filter(s -> s.getCategoryId().equals(categoryId))
            .map(CategorySpendDto::getTotalAmount)
            .findFirst()
            .orElse(BigDecimal.ZERO);

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        BigDecimal dailyAvg = days == 0 ? BigDecimal.ZERO
            : totalSpend.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

        String categoryName = spends.stream()
            .filter(s -> s.getCategoryId().equals(categoryId))
            .map(CategorySpendDto::getCategoryName)
            .findFirst()
            .orElse("Unknown");

        return new BurnRateResponse(categoryId, categoryName, totalSpend, dailyAvg, (int) days);
    }

    public MonthlyTrendResponse getMonthlyTrends(Long userId, int monthsBack) {
        LocalDate since = LocalDate.now().minusMonths(monthsBack).withDayOfMonth(1);
        List<Object[]> rows = transactionRepository.findMonthlyTrends(userId, since);

        List<MonthlyTrendResponse.MonthlyDataPoint> points = rows.stream().map(row -> {
            String month       = (String) row[0];
            BigDecimal income   = toBigDecimal(row[1]);
            BigDecimal expenses = toBigDecimal(row[2]);
            return new MonthlyTrendResponse.MonthlyDataPoint(
                month, income, expenses, income.subtract(expenses)
            );
        }).toList();

        return new MonthlyTrendResponse(points);
    }

    public SpendingBreakdownResponse getSpendingBreakdown(Long userId, LocalDate start, LocalDate end) {
        List<CategorySpendDto> spends = transactionRepository
            .sumExpensesByCategoryAndDateRange(userId, start, end);

        BigDecimal total = spends.stream()
            .map(CategorySpendDto::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SpendingBreakdownResponse.CategoryShare> shares = spends.stream()
            .map(s -> {
                double pct = total.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                    : s.getTotalAmount().divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                return new SpendingBreakdownResponse.CategoryShare(
                    s.getCategoryId(), s.getCategoryName(), s.getTotalAmount(),
                    Math.round(pct * 100.0) / 100.0
                );
            })
            .toList();

        String period = start + " to " + end;
        return new SpendingBreakdownResponse(period, total, shares);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }
}
