package com.studies.account;

import com.studies.account.exception.AccountNotExistsException;
import com.studies.account.exception.AmountMustBePositiveException;
import com.studies.account.exception.DuplicateAccountException;
import com.studies.account.exception.InsufficientFundsException;
import com.studies.account.exception.SameAccountTransferException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AccountServiceImpl implements AccountService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public void createAccount(String accountId, long initialBalanceCents) {
        checkThatAmountIsPositive(initialBalanceCents);

        Account existing = accounts.putIfAbsent(accountId, new Account(accountId, initialBalanceCents));

        if (existing != null) {
            throw new DuplicateAccountException(accountId);
        }
    }

    @Override
    public long getBalance(String accountId) {
        Account account = getAccount(accountId);
        synchronized (account) {
            return account.getBalanceInCents();
        }
    }

    @Override
    public void deposit(String accountId, long amountCents) {
        checkThatAmountIsPositive(amountCents);

        Account account = getAccount(accountId);

        synchronized (account) {
            account.deposit(amountCents);
        }
    }

    @Override
    public void withdraw(String accountId, long amountCents) {
        checkThatAmountIsPositive(amountCents);

        Account account = getAccount(accountId);
        synchronized (account) {
            if (account.getBalanceInCents() < amountCents) {
                throw new InsufficientFundsException(accountId);
            }
            account.withdraw(amountCents);
        }
    }

    @Override
    public void transfer(String fromAccountId, String toAccountId, long amountCents) {
        checkThatAmountIsPositive(amountCents);

        if (fromAccountId.equals(toAccountId)) {
            throw new SameAccountTransferException(fromAccountId);
        }

        Account fromAccount = getAccount(fromAccountId);
        Account toAccount = getAccount(toAccountId);

        Account account1 = fromAccountId.compareTo(toAccountId) < 0 ? fromAccount : toAccount;
        Account account2 = toAccountId.compareTo(fromAccountId) < 0 ? fromAccount : toAccount;

        synchronized (account1) {
            synchronized (account2) {
                if (fromAccount.getBalanceInCents() < amountCents) {
                    throw new InsufficientFundsException(fromAccountId);
                }
                fromAccount.withdraw(amountCents);
                toAccount.deposit(amountCents);
            }
        }
    }

    @Override
    public void transfer(String idempotencyKey, String fromAccountId, String toAccountId, long amountCents) {

    }

    @Override
    public Set<Transaction> getHistory(String accountId) {
        Account account = getAccount(accountId);
        synchronized (account) {
            return account.getHistory();
        }
    }

    private Account getAccount(String accountId) {
        Account account = accounts.get(accountId);

        if (account == null) {
            throw new AccountNotExistsException(accountId);
        }
        return account;
    }

    private static void checkThatAmountIsPositive(long amountCents) {
        if (amountCents < 0) {
            throw new AmountMustBePositiveException(amountCents);
        }
    }
}
