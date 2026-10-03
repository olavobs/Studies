package com.studies.account;

import com.studies.account.exception.AccountNotExistsException;
import com.studies.account.exception.AmountMustBePositiveException;
import com.studies.account.exception.InsufficientFundsException;
import com.studies.account.exception.SameAccountTransferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TransferTest {

    // "acc-b" is alphabetically after "acc-a", which exercises both lock-ordering branches
    private static final String ACC_A = "acc-a";
    private static final String ACC_B = "acc-b";
    private static final long INITIAL_BALANCE = 1000L;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl();
        accountService.createAccount(ACC_A, INITIAL_BALANCE);
        accountService.createAccount(ACC_B, INITIAL_BALANCE);
    }

    @Nested
    class HappyPath {

        @Test
        void reducesSourceBalanceAndIncreasesDestinationBalance() {
            accountService.transfer(ACC_A, ACC_B, 300L);

            assertEquals(INITIAL_BALANCE - 300L, accountService.getBalance(ACC_A));
            assertEquals(INITIAL_BALANCE + 300L, accountService.getBalance(ACC_B));
        }

        @Test
        void worksWhenSourceIsAlphabeticallyAfterDestination() {
            // exercises the branch where toAccountId < fromAccountId in lock ordering
            accountService.transfer(ACC_B, ACC_A, 300L);

            assertEquals(INITIAL_BALANCE - 300L, accountService.getBalance(ACC_B));
            assertEquals(INITIAL_BALANCE + 300L, accountService.getBalance(ACC_A));
        }

        @Test
        void allowsTransferringEntireSourceBalance() {
            accountService.transfer(ACC_A, ACC_B, INITIAL_BALANCE);

            assertEquals(0L, accountService.getBalance(ACC_A));
            assertEquals(INITIAL_BALANCE * 2, accountService.getBalance(ACC_B));
        }

        @Test
        void allowsZeroAmountTransfer() {
            accountService.transfer(ACC_A, ACC_B, 0L);

            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_A));
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_B));
        }

        @Test
        void multipleSequentialTransfersAccumulateCorrectly() {
            accountService.transfer(ACC_A, ACC_B, 100L);
            accountService.transfer(ACC_A, ACC_B, 200L);
            accountService.transfer(ACC_B, ACC_A, 50L);

            assertEquals(INITIAL_BALANCE - 100L - 200L + 50L, accountService.getBalance(ACC_A));
            assertEquals(INITIAL_BALANCE + 100L + 200L - 50L, accountService.getBalance(ACC_B));
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
        void throwsAmountMustBePositiveException_whenAmountIsNegative(long negativeAmount) {
            assertThrows(AmountMustBePositiveException.class,
                    () -> accountService.transfer(ACC_A, ACC_B, negativeAmount));

            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_A));
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_B));
        }

        @Test
        void throwsAccountNotExistsException_whenSourceDoesNotExist() {
            assertThrows(AccountNotExistsException.class,
                    () -> accountService.transfer("unknown", ACC_B, 100L));
        }

        @Test
        void throwsAccountNotExistsException_whenDestinationDoesNotExist() {
            assertThrows(AccountNotExistsException.class,
                    () -> accountService.transfer(ACC_A, "unknown", 100L));
        }

        @Test
        void throwsInsufficientFundsException_whenSourceBalanceIsInsufficient() {
            assertThrows(InsufficientFundsException.class,
                    () -> accountService.transfer(ACC_A, ACC_B, INITIAL_BALANCE + 1));

            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_A));
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_B));
        }

        @Test
        void throwsInsufficientFundsException_whenSourceIsAlphabeticallyAfterDestination() {
            // guards against the bug where insufficient-funds check uses lock-order account instead of source
            assertThrows(InsufficientFundsException.class,
                    () -> accountService.transfer(ACC_B, ACC_A, INITIAL_BALANCE + 1));

            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_A));
            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_B));
        }

        @Test
        void throwsException_whenTransferringToSameAccount() {
            assertThrows(SameAccountTransferException.class,
                    () -> accountService.transfer(ACC_A, ACC_A, 100L));

            assertEquals(INITIAL_BALANCE, accountService.getBalance(ACC_A));
        }
    }

    @Nested
    class Concurrency {

        @Test
        void preservesTotalSystemBalance_underConcurrentTransfers() throws InterruptedException {
            int threads = 20;
            long transferAmount = 10L;
            CountDownLatch latch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < threads; i++) {
                final boolean aToB = i % 2 == 0;
                executor.submit(() -> {
                    try {
                        latch.await();
                        if (aToB) {
                            accountService.transfer(ACC_A, ACC_B, transferAmount);
                        } else {
                            accountService.transfer(ACC_B, ACC_A, transferAmount);
                        }
                    } catch (InsufficientFundsException | InterruptedException ignored) {
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            long totalBalance = accountService.getBalance(ACC_A) + accountService.getBalance(ACC_B);
            assertEquals(INITIAL_BALANCE * 2, totalBalance);
        }

        @Test
        void doesNotDeadlock_whenTransferringInOppositeDirectionsSimultaneously() throws InterruptedException {
            int rounds = 50;
            CountDownLatch latch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < rounds; i++) {
                        try { accountService.transfer(ACC_A, ACC_B, 1L); }
                        catch (InsufficientFundsException ignored) {}
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < rounds; i++) {
                        try { accountService.transfer(ACC_B, ACC_A, 1L); }
                        catch (InsufficientFundsException ignored) {}
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            latch.countDown();
            executor.shutdown();
            boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);

            assertTrue(finished, "Deadlock detected: threads did not finish within timeout");

            long totalBalance = accountService.getBalance(ACC_A) + accountService.getBalance(ACC_B);
            assertEquals(INITIAL_BALANCE * 2, totalBalance);
        }

        @Test
        void doesNotLoseMoney_whenMultipleThreadsTransferFromSameSource() throws InterruptedException {
            int threads = 10;
            long transferAmount = 50L;
            String sink = "sink";
            accountService.createAccount(sink, 0L);

            CountDownLatch latch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        try { accountService.transfer(ACC_A, sink, transferAmount); }
                        catch (InsufficientFundsException ignored) {}
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            long totalBalance = accountService.getBalance(ACC_A)
                    + accountService.getBalance(ACC_B)
                    + accountService.getBalance(sink);
            assertEquals(INITIAL_BALANCE * 2, totalBalance);
        }

        @Test
        void doesNotDuplicateMoney_whenMultipleThreadsTransferToSameDestination() throws InterruptedException {
            int threads = 10;
            long transferAmount = 50L;
            String source = "source";
            accountService.createAccount(source, threads * transferAmount);

            CountDownLatch latch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        try { accountService.transfer(source, ACC_A, transferAmount); }
                        catch (InsufficientFundsException ignored) {}
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            long totalBalance = accountService.getBalance(ACC_A)
                    + accountService.getBalance(ACC_B)
                    + accountService.getBalance(source);
            assertEquals(INITIAL_BALANCE * 2 + threads * transferAmount, totalBalance);
        }
    }
}
