package com.studies.account;

import java.util.Set;

public interface AccountService {

    void createAccount(String accountId, long initialBalanceCents);

    long getBalance(String accountId);

    void deposit(String accountId, long amountCents);

    void withdraw(String accountId, long amountCents);

    void transfer(String fromAccountId, String toAccountId, long amountCents);

    void transfer(String idempotencyKey, String fromAccountId, String toAccountId, long amountCents);

    Set<Transaction> getHistory(String accountId);
}