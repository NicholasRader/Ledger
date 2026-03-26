package com.financetracker.domain.repository;

import com.financetracker.domain.entity.Transaction;
import com.financetracker.domain.entity.Transaction.TransactionType;
import com.financetracker.dto.analytics.CategorySpendDto;
import com.financetracker.dto.analytics.MonthlyTrendDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Paginated list of transactions for a user across all accounts
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.account.user.id = :userId
        ORDER BY t.transactionDate DESC
    """)
    Page<Transaction> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    Optional<Transaction> findByIdAndAccountUserId(Long id, Long userId);

    // Total income for a user in a date range
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.account.user.id = :userId
          AND t.type = 'INCOME'
          AND t.transactionDate BETWEEN :start AND :end
    """)
    BigDecimal sumIncomeByUserAndDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    // Total expenses for a user in a date range
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.account.user.id = :userId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :start AND :end
    """)
    BigDecimal sumExpensesByUserAndDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    // Spending per category in a date range (for budget checks & analytics)
    @Query("""
        SELECT new com.financetracker.dto.analytics.CategorySpendDto(
            c.id, c.name, COALESCE(SUM(t.amount), 0)
        )
        FROM Transaction t
        JOIN t.category c
        WHERE t.account.user.id = :userId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :start AND :end
        GROUP BY c.id, c.name
    """)
    List<CategorySpendDto> sumExpensesByCategoryAndDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    // Month-over-month trend: total income and expenses per month
    @Query(value = """
        SELECT
            TO_CHAR(DATE_TRUNC('month', t.transaction_date), 'YYYY-MM') AS month,
            COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0)  AS total_income,
            COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS total_expenses
        FROM transactions t
        JOIN accounts a ON t.account_id = a.id
        WHERE a.user_id = :userId
          AND t.transaction_date >= :since
        GROUP BY DATE_TRUNC('month', t.transaction_date)
        ORDER BY DATE_TRUNC('month', t.transaction_date)
    """, nativeQuery = true)
    List<Object[]> findMonthlyTrends(
        @Param("userId") Long userId,
        @Param("since") LocalDate since
    );

    // Average and std dev of spending in a category — used for anomaly detection
    @Query(value = """
        SELECT
            AVG(t.amount) AS avg_amount,
            STDDEV(t.amount) AS stddev_amount
        FROM transactions t
        JOIN accounts a ON t.account_id = a.id
        WHERE a.user_id = :userId
          AND t.category_id = :categoryId
          AND t.type = 'EXPENSE'
          AND t.transaction_date >= :since
    """, nativeQuery = true)
    Object[] findCategorySpendStats(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("since") LocalDate since
    );

    // Find recent transactions in a category (used to surface anomalies)
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.account.user.id = :userId
          AND t.category.id = :categoryId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :start AND :end
    """)
    List<Transaction> findByUserCategoryAndDateRange(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );
}
