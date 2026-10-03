package com.studies.account.exception;

public class AccountNotExistsException extends RuntimeException {
    public AccountNotExistsException(String accountId) {
        super("Account with id " + accountId + " does not exist");
    }
}
