package com.ledger.dto.analytics;

import java.math.BigDecimal;

/**
 * Used to map native query results from TransactionRepository.findMonthlyTrends().
 */
public class MonthlyTrendDto {
    private final String month;
    private final BigDecimal totalIncome;
    private final BigDecimal totalExpenses;

    public MonthlyTrendDto(String month, BigDecimal totalIncome, BigDecimal totalExpenses) {
        this.month = month;
        this.totalIncome = totalIncome;
        this.totalExpenses = totalExpenses;
    }

    public String getMonth()              { return month; }
    public BigDecimal getTotalIncome()    { return totalIncome; }
    public BigDecimal getTotalExpenses()  { return totalExpenses; }
}
