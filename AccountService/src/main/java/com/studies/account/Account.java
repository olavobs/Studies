package com.studies.account;

import java.util.HashSet;
import java.util.Set;

public class Account {

    public Account(String id, Long initialBalanceInCents) {
        this.id = id;
        this.balanceInCents = initialBalanceInCents;
    }

    private final String id;

    private Set<Transaction> transactions;

    private long balanceInCents;

    public long getBalanceInCents() {
        return balanceInCents;
    }

    public String getId() {
        return this.id;
    }

    public void deposit(Long amountInCents) {
        this.balanceInCents += amountInCents;
    }

    public void withdraw(Long amountInCents) {
        this.balanceInCents -= amountInCents;
    }

    public Set<Transaction> getHistory() {
        return Set.copyOf(transactions);
    }

    public void addTransaction(Transaction transaction) {
        if (transactions == null) {
            transactions = new HashSet<>();
        }
        this.transactions.add(transaction);
    }
}
