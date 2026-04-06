package com.ledger.service;

import com.ledger.domain.entity.Account;
import com.ledger.domain.entity.Category;
import com.ledger.domain.entity.RecurringPayment;
import com.ledger.domain.entity.User;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.CategoryRepository;
import com.ledger.domain.repository.RecurringPaymentRepository;
import com.ledger.dto.recurring.RecurringPaymentDtos.*;
import com.ledger.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurringPaymentService {

    private final RecurringPaymentRepository recurringPaymentRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public RecurringPaymentResponse create(CreateRecurringPaymentRequest request, User user) {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.accountId()));

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }

        RecurringPayment payment = RecurringPayment.builder()
            .user(user)
            .account(account)
            .category(category)
            .name(request.name())
            .amount(request.amount())
            .currency(request.currency() != null ? request.currency() : "USD")
            .frequency(request.frequency())
            .nextDueDate(request.nextDueDate())
            .build();

        return toResponse(recurringPaymentRepository.save(payment));
    }

    public List<RecurringPaymentResponse> getAllForUser(Long userId) {
        return recurringPaymentRepository.findByUserId(userId)
            .stream().map(this::toResponse).toList();
    }

    public RecurringPaymentResponse getById(Long id, Long userId) {
        return recurringPaymentRepository.findByIdAndUserId(id, userId)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Recurring payment not found: " + id));
    }

    @Transactional
    public RecurringPaymentResponse update(Long id, Long userId, UpdateRecurringPaymentRequest request) {
        RecurringPayment payment = recurringPaymentRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Recurring payment not found: " + id));

        if (request.name() != null)         payment.setName(request.name());
        if (request.amount() != null)       payment.setAmount(request.amount());
        if (request.frequency() != null)    payment.setFrequency(request.frequency());
        if (request.nextDueDate() != null)  payment.setNextDueDate(request.nextDueDate());
        if (request.active() != null)       payment.setActive(request.active());

        return toResponse(recurringPaymentRepository.save(payment));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        RecurringPayment payment = recurringPaymentRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Recurring payment not found: " + id));
        recurringPaymentRepository.delete(payment);
    }

    public RecurringPaymentResponse toResponse(RecurringPayment p) {
        return new RecurringPaymentResponse(
            p.getId(),
            p.getAccount().getId(),
            p.getAccount().getName(),
            p.getCategory() != null ? p.getCategory().getId() : null,
            p.getCategory() != null ? p.getCategory().getName() : null,
            p.getName(),
            p.getAmount(),
            p.getCurrency(),
            p.getFrequency(),
            p.getNextDueDate(),
            p.isActive(),
            p.getCreatedAt()
        );
    }
}
