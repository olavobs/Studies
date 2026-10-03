package com.studies.account.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId) {
        super("Not enough funds in account with accountId: " + accountId);
    }
}
