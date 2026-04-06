package com.ledger.service;

import com.ledger.domain.entity.Account;
import com.ledger.domain.entity.Account.AccountType;
import com.ledger.domain.entity.User;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.dto.account.AccountDtos.*;
import com.ledger.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @InjectMocks private AccountService accountService;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L).email("test@example.com").fullName("Test User").role("ROLE_USER")
            .build();

        testAccount = Account.builder()
            .id(1L).user(testUser).name("Checking")
            .accountType(AccountType.CHECKING)
            .balance(new BigDecimal("1000.00"))
            .currency("USD")
            .build();
        ReflectionTestUtils.setField(testAccount, "createdAt", LocalDateTime.now());
    }

    @Nested
    @DisplayName("createAccount()")
    class CreateAccount {

        @Test
        @DisplayName("should create account with provided initial balance")
        void createAccount_withInitialBalance() {
            CreateAccountRequest request = new CreateAccountRequest(
                "Savings", AccountType.SAVINGS, new BigDecimal("5000.00"), "USD"
            );
            when(accountRepository.save(any())).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 2L);
                ReflectionTestUtils.setField(a, "createdAt", LocalDateTime.now());
                return a;
            });

            AccountResponse response = accountService.createAccount(request, testUser);

            assertThat(response.balance()).isEqualByComparingTo("5000.00");
            assertThat(response.name()).isEqualTo("Savings");
            assertThat(response.accountType()).isEqualTo(AccountType.SAVINGS);
        }

        @Test
        @DisplayName("should default balance to zero when not provided")
        void createAccount_defaultsBalanceToZero() {
            CreateAccountRequest request = new CreateAccountRequest(
                "Empty Account", AccountType.CHECKING, null, "USD"
            );
            when(accountRepository.save(any())).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 3L);
                ReflectionTestUtils.setField(a, "createdAt", LocalDateTime.now());
                return a;
            });

            AccountResponse response = accountService.createAccount(request, testUser);

            assertThat(response.balance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("should default currency to USD when not provided")
        void createAccount_defaultsCurrencyToUsd() {
            CreateAccountRequest request = new CreateAccountRequest(
                "Account", AccountType.CHECKING, BigDecimal.ZERO, null
            );
            when(accountRepository.save(any())).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 4L);
                ReflectionTestUtils.setField(a, "createdAt", LocalDateTime.now());
                return a;
            });

            AccountResponse response = accountService.createAccount(request, testUser);

            assertThat(response.currency()).isEqualTo("USD");
        }
    }

    @Nested
    @DisplayName("getAccountsForUser()")
    class GetAccountsForUser {

        @Test
        @DisplayName("should return all accounts for user")
        void getAccountsForUser_returnsAll() {
            Account second = Account.builder()
                .id(2L).user(testUser).name("Savings")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("5000.00")).currency("USD")
                .build();
            ReflectionTestUtils.setField(second, "createdAt", LocalDateTime.now());

            when(accountRepository.findByUserId(1L)).thenReturn(List.of(testAccount, second));

            List<AccountResponse> result = accountService.getAccountsForUser(1L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AccountResponse::name)
                .containsExactly("Checking", "Savings");
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        void getAccountsForUser_empty() {
            when(accountRepository.findByUserId(1L)).thenReturn(List.of());

            List<AccountResponse> result = accountService.getAccountsForUser(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAccountById()")
    class GetAccountById {

        @Test
        @DisplayName("should return account when found")
        void getAccountById_found() {
            when(accountRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(testAccount));

            AccountResponse response = accountService.getAccountById(1L, 1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Checking");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void getAccountById_notFound() {
            when(accountRepository.findByIdAndUserId(99L, 1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountById(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
        }
    }

    @Nested
    @DisplayName("updateAccount()")
    class UpdateAccount {

        @Test
        @DisplayName("should update name when provided")
        void updateAccount_updatesName() {
            UpdateAccountRequest request = new UpdateAccountRequest("New Name", null, null);
            when(accountRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            AccountResponse response = accountService.updateAccount(1L, 1L, request);

            assertThat(testAccount.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("should throw when account not found")
        void updateAccount_notFound() {
            when(accountRepository.findByIdAndUserId(99L, 1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.updateAccount(
                99L, 1L, new UpdateAccountRequest("X", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteAccount()")
    class DeleteAccount {

        @Test
        @DisplayName("should delete account when found")
        void deleteAccount_success() {
            when(accountRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(testAccount));

            accountService.deleteAccount(1L, 1L);

            verify(accountRepository).delete(testAccount);
        }

        @Test
        @DisplayName("should throw when account not found")
        void deleteAccount_notFound() {
            when(accountRepository.findByIdAndUserId(99L, 1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.deleteAccount(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(accountRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("adjustBalance()")
    class AdjustBalance {

        @Test
        @DisplayName("should add positive delta to balance")
        void adjustBalance_positive() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            accountService.adjustBalance(1L, new BigDecimal("500.00"));

            assertThat(testAccount.getBalance()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("should subtract negative delta from balance")
        void adjustBalance_negative() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            accountService.adjustBalance(1L, new BigDecimal("-200.00"));

            assertThat(testAccount.getBalance()).isEqualByComparingTo("800.00");
        }
    }
}
