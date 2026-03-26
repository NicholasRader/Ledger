package com.financetracker.service;

import com.financetracker.domain.entity.Account;
import com.financetracker.domain.entity.User;
import com.financetracker.domain.repository.AccountRepository;
import com.financetracker.dto.account.AccountDtos.*;
import com.financetracker.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.financetracker.config.CacheConfig.CACHE_NET_WORTH;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CACHE_NET_WORTH, key = "#user.id")
    public AccountResponse createAccount(CreateAccountRequest request, User user) {
        Account account = Account.builder()
            .user(user)
            .name(request.name())
            .accountType(request.accountType())
            .balance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO)
            .currency(request.currency() != null ? request.currency() : "USD")
            .build();

        return toResponse(accountRepository.save(account));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<AccountResponse> getAccountsForUser(Long userId) {
        return accountRepository.findByUserId(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public AccountResponse getAccountById(Long accountId, Long userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CACHE_NET_WORTH, key = "#userId")
    public AccountResponse updateAccount(Long accountId, Long userId, UpdateAccountRequest request) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (request.name() != null)        account.setName(request.name());
        if (request.accountType() != null) account.setAccountType(request.accountType());
        if (request.currency() != null)    account.setCurrency(request.currency());

        return toResponse(accountRepository.save(account));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CACHE_NET_WORTH, key = "#userId")
    public void deleteAccount(Long accountId, Long userId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        accountRepository.delete(account);
    }

    // ── Balance Adjustment (called internally by TransactionService) ──────────

    @Transactional
    public void adjustBalance(Long accountId, BigDecimal delta) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        account.setBalance(account.getBalance().add(delta));
        accountRepository.save(account);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getName(),
            account.getAccountType(),
            account.getBalance(),
            account.getCurrency(),
            account.getCreatedAt()
        );
    }
}
