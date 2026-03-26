package com.financetracker.dto.analytics;

import java.math.BigDecimal;

/**
 * JPQL constructor projection used in TransactionRepository.
 * Must be a standalone class (not a nested record) for JPQL "new" syntax.
 */
public class CategorySpendDto {

    private final Long categoryId;
    private final String categoryName;
    private final BigDecimal totalAmount;

    public CategorySpendDto(Long categoryId, String categoryName, BigDecimal totalAmount) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.totalAmount = totalAmount;
    }

    public Long getCategoryId()     { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
