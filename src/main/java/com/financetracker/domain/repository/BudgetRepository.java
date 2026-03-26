package com.financetracker.domain.repository;

import com.financetracker.domain.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndMonth(Long userId, LocalDate month);

    Optional<Budget> findByUserIdAndCategoryIdAndMonth(Long userId, Long categoryId, LocalDate month);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    List<Budget> findByUserId(Long userId);
}
