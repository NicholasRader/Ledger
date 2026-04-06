package com.ledger.service;

import com.ledger.domain.entity.Account;
import com.ledger.domain.entity.Category;
import com.ledger.domain.entity.Transaction;
import com.ledger.domain.entity.Transaction.TransactionType;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.CategoryRepository;
import com.ledger.domain.repository.TransactionRepository;
import com.ledger.dto.transaction.TransactionDtos.*;
import com.ledger.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.ledger.config.CacheConfig.*;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_NET_WORTH,      key = "#userId"),
        @CacheEvict(value = CACHE_BUDGET_SUMMARY, key = "#userId"),
        @CacheEvict(value = CACHE_CASH_FLOW,      key = "#userId")
    })
    public TransactionResponse createTransaction(CreateTransactionRequest request, Long userId) {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.accountId()));

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
        }

        Transaction transaction = Transaction.builder()
            .account(account)
            .category(category)
            .amount(request.amount())
            .currency(request.currency() != null ? request.currency() : account.getCurrency())
            .type(request.type())
            .description(request.description())
            .merchant(request.merchant())
            .transactionDate(request.transactionDate())
            .build();

        transactionRepository.save(transaction);

        // Update account balance: income adds, expense subtracts
        adjustAccountBalance(account, request.type(), request.amount());

        return toResponse(transaction);
    }

    public PagedTransactionResponse getTransactions(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> result = transactionRepository.findAllByUserId(userId, pageable);

        return new PagedTransactionResponse(
            result.getContent().stream().map(this::toResponse).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public TransactionResponse getTransactionById(Long transactionId, Long userId) {
        return transactionRepository.findByIdAndAccountUserId(transactionId, userId)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_NET_WORTH,      key = "#userId"),
        @CacheEvict(value = CACHE_BUDGET_SUMMARY, key = "#userId"),
        @CacheEvict(value = CACHE_CASH_FLOW,      key = "#userId")
    })
    public TransactionResponse updateTransaction(Long transactionId, Long userId, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndAccountUserId(transactionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        // If amount or type changed, reverse the old balance effect and apply new
        if (request.amount() != null || request.type() != null) {
            BigDecimal oldAmount = transaction.getAmount();
            TransactionType oldType = transaction.getType();

            // Reverse old effect
            adjustAccountBalance(transaction.getAccount(), oldType, oldAmount.negate());

            BigDecimal newAmount = request.amount() != null ? request.amount() : oldAmount;
            TransactionType newType = request.type() != null ? request.type() : oldType;

            transaction.setAmount(newAmount);
            transaction.setType(newType);

            // Apply new effect
            adjustAccountBalance(transaction.getAccount(), newType, newAmount);
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            transaction.setCategory(category);
        }
        if (request.description() != null)      transaction.setDescription(request.description());
        if (request.merchant() != null)         transaction.setMerchant(request.merchant());
        if (request.transactionDate() != null)  transaction.setTransactionDate(request.transactionDate());

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_NET_WORTH,      key = "#userId"),
        @CacheEvict(value = CACHE_BUDGET_SUMMARY, key = "#userId"),
        @CacheEvict(value = CACHE_CASH_FLOW,      key = "#userId")
    })
    public void deleteTransaction(Long transactionId, Long userId) {
        Transaction transaction = transactionRepository.findByIdAndAccountUserId(transactionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        // Reverse the balance effect before deleting
        adjustAccountBalance(transaction.getAccount(), transaction.getType(), transaction.getAmount().negate());
        transactionRepository.delete(transaction);
    }

    private void adjustAccountBalance(Account account, TransactionType type, BigDecimal amount) {
        BigDecimal delta = switch (type) {
            case INCOME   -> amount;
            case EXPENSE  -> amount.negate();
            case TRANSFER -> BigDecimal.ZERO; // Transfers handled separately
        };
        account.setBalance(account.getBalance().add(delta));
        accountRepository.save(account);
    }

    public TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
            t.getId(),
            t.getAccount().getId(),
            t.getAccount().getName(),
            t.getCategory() != null ? t.getCategory().getId() : null,
            t.getCategory() != null ? t.getCategory().getName() : null,
            t.getAmount(),
            t.getCurrency(),
            t.getType(),
            t.getDescription(),
            t.getMerchant(),
            t.getTransactionDate(),
            t.getCreatedAt()
        );
    }
}
