package com.studies.account.exception;

public class AmountMustBePositiveException extends RuntimeException {

    public AmountMustBePositiveException(long initialBalanceCents) {
        super("Amount should always be positive: " + initialBalanceCents);
    }
}
