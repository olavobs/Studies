package com.studies.account.exception;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String accountId) {
        super("Account with id " + accountId + " already exists");
    }
}
