package com.studies.account;

import com.studies.account.exception.AccountNotExistsException;
import com.studies.account.exception.AmountMustBePositiveException;
import com.studies.account.exception.DuplicateAccountException;
import com.studies.account.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceImplTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final long INITIAL_BALANCE = 1000L;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl();
    }

    @Nested
    class CreateAccount {

        @Test
        void balanceReflectsInitialDeposit() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void allowsZeroAsInitialBalance() {
            accountService.createAccount(ACCOUNT_ID, 0L);
            assertEquals(0L, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void allowsMultipleDistinctAccounts() {
            accountService.createAccount("acc-1", 100L);
            accountService.createAccount("acc-2", 200L);

            assertEquals(100L, accountService.getBalance("acc-1"));
            assertEquals(200L, accountService.getBalance("acc-2"));
        }

        @Test
        void doesNotOverwriteExistingBalance_whenDuplicateCreationIsAttempted() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            assertThrows(DuplicateAccountException.class,
                    () -> accountService.createAccount(ACCOUNT_ID, 9999L));

            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACCOUNT_ID));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
        void throwsAmountMustBePositiveException_whenInitialBalanceIsNegative(long negativeAmount) {
            assertThrows(AmountMustBePositiveException.class,
                    () -> accountService.createAccount(ACCOUNT_ID, negativeAmount));
        }
    }

    @Nested
    class GetBalance {

        @Test
        void throwsAccountNotExistsException_whenAccountDoesNotExist() {
            assertThrows(AccountNotExistsException.class,
                    () -> accountService.getBalance("unknown"));
        }

    }

    @Nested
    class Deposit {

        @Test
        void increasesBalance() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            accountService.deposit(ACCOUNT_ID, 500L);
            assertEquals(INITIAL_BALANCE + 500L, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void accumulatesMultipleDeposits() {
            accountService.createAccount(ACCOUNT_ID, 0L);
            accountService.deposit(ACCOUNT_ID, 100L);
            accountService.deposit(ACCOUNT_ID, 200L);
            accountService.deposit(ACCOUNT_ID, 300L);
            assertEquals(600L, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void allowsZeroDeposit() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            accountService.deposit(ACCOUNT_ID, 0L);
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void throwsAccountNotExistsException_whenAccountDoesNotExist() {
            assertThrows(AccountNotExistsException.class,
                    () -> accountService.deposit("unknown", 100L));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
        void throwsAmountMustBePositiveException_whenAmountIsNegative(long negativeAmount) {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            assertThrows(AmountMustBePositiveException.class,
                    () -> accountService.deposit(ACCOUNT_ID, negativeAmount));
        }
    }

    @Nested
    class Withdraw {

        @Test
        void decreasesBalance() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            accountService.withdraw(ACCOUNT_ID, 400L);
            assertEquals(INITIAL_BALANCE - 400L, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void allowsWithdrawingEntireBalance() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            accountService.withdraw(ACCOUNT_ID, INITIAL_BALANCE);
            assertEquals(0L, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void allowsZeroWithdrawal() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            accountService.withdraw(ACCOUNT_ID, 0L);
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void doesNotAlterBalance_whenWithdrawalFails() {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            assertThrows(InsufficientFundsException.class,
                    () -> accountService.withdraw(ACCOUNT_ID, INITIAL_BALANCE + 1));
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACCOUNT_ID));
        }

        @Test
        void throwsAccountNotExistsException_whenAccountDoesNotExist() {
            assertThrows(AccountNotExistsException.class,
                    () -> accountService.withdraw("unknown", 100L));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
        void throwsAmountMustBePositiveException_whenAmountIsNegative(long negativeAmount) {
            accountService.createAccount(ACCOUNT_ID, INITIAL_BALANCE);
            assertThrows(AmountMustBePositiveException.class,
                    () -> accountService.withdraw(ACCOUNT_ID, negativeAmount));
        }
    }

    @Nested
    class Concurrency {

        @Test
        void maintainsCorrectBalance_underConcurrentDeposits() throws InterruptedException {
            int threads = 10;
            long depositAmount = 100L;
            accountService.createAccount(ACCOUNT_ID, 0L);

            CountDownLatch latch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        accountService.deposit(ACCOUNT_ID, depositAmount);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            assertEquals(threads * depositAmount, accountService.getBalance(ACCOUNT_ID));
        }
    }
}
