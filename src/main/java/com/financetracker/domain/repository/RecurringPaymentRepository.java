package com.financetracker.domain.repository;

import com.financetracker.domain.entity.RecurringPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long> {

    List<RecurringPayment> findByUserId(Long userId);

    Optional<RecurringPayment> findByIdAndUserId(Long id, Long userId);

    // Find all active recurring payments due on or before a given date (used by scheduler)
    List<RecurringPayment> findByActiveIsTrueAndNextDueDateLessThanEqual(LocalDate date);
}
