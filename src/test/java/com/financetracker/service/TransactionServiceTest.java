package com.financetracker.service;

import com.financetracker.domain.entity.*;
import com.financetracker.domain.entity.Transaction.TransactionType;
import com.financetracker.domain.repository.AccountRepository;
import com.financetracker.domain.repository.CategoryRepository;
import com.financetracker.domain.repository.TransactionRepository;
import com.financetracker.dto.transaction.TransactionDtos.*;
import com.financetracker.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L).email("test@example.com").fullName("Test User").role("ROLE_USER")
            .build();

        testAccount = Account.builder()
            .id(1L).user(testUser).name("Checking").currency("USD")
            .accountType(Account.AccountType.CHECKING)
            .balance(new BigDecimal("1000.00"))
            .build();
    }

    @Test
    @DisplayName("createTransaction() - EXPENSE should deduct from account balance")
    void createTransaction_expense_reducesBalance() {
        CreateTransactionRequest request = new CreateTransactionRequest(
            1L, null,
            new BigDecimal("50.00"), "USD",
            TransactionType.EXPENSE,
            "Coffee", "Starbucks",
            LocalDate.now()
        );

        Transaction savedTransaction = Transaction.builder()
            .id(1L).account(testAccount).amount(new BigDecimal("50.00"))
            .currency("USD").type(TransactionType.EXPENSE)
            .description("Coffee").merchant("Starbucks")
            .transactionDate(LocalDate.now())
            .build();
        ReflectionTestUtils.setField(savedTransaction, "createdAt", LocalDateTime.now());

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any())).thenReturn(savedTransaction);
        when(accountRepository.save(any())).thenReturn(testAccount);

        TransactionResponse response = transactionService.createTransaction(request, 1L);

        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        // Balance should have been reduced
        assertThat(testAccount.getBalance()).isEqualByComparingTo("950.00");
    }

    @Test
    @DisplayName("createTransaction() - INCOME should increase account balance")
    void createTransaction_income_increasesBalance() {
        CreateTransactionRequest request = new CreateTransactionRequest(
            1L, null,
            new BigDecimal("2000.00"), "USD",
            TransactionType.INCOME,
            "Paycheck", "Employer",
            LocalDate.now()
        );

        Transaction savedTransaction = Transaction.builder()
            .id(2L).account(testAccount).amount(new BigDecimal("2000.00"))
            .currency("USD").type(TransactionType.INCOME)
            .transactionDate(LocalDate.now())
            .build();
        ReflectionTestUtils.setField(savedTransaction, "createdAt", LocalDateTime.now());

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any())).thenReturn(savedTransaction);
        when(accountRepository.save(any())).thenReturn(testAccount);

        transactionService.createTransaction(request, 1L);

        assertThat(testAccount.getBalance()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("createTransaction() - should throw when account not found")
    void createTransaction_accountNotFound_throws() {
        CreateTransactionRequest request = new CreateTransactionRequest(
            99L, null, new BigDecimal("10.00"), "USD",
            TransactionType.EXPENSE, null, null, LocalDate.now()
        );

        when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(request, 1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Account not found");
    }

    @Test
    @DisplayName("deleteTransaction() - should reverse balance on delete")
    void deleteTransaction_reversesBalance() {
        Transaction transaction = Transaction.builder()
            .id(1L).account(testAccount)
            .amount(new BigDecimal("100.00"))
            .type(TransactionType.EXPENSE)
            .transactionDate(LocalDate.now())
            .build();

        // Simulate balance after expense was applied
        testAccount.setBalance(new BigDecimal("900.00"));

        when(transactionRepository.findByIdAndAccountUserId(1L, 1L))
            .thenReturn(Optional.of(transaction));
        when(accountRepository.save(any())).thenReturn(testAccount);

        transactionService.deleteTransaction(1L, 1L);

        // Balance should be restored to 1000
        assertThat(testAccount.getBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository).delete(transaction);
    }
}
