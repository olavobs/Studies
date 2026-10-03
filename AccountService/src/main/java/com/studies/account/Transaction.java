package com.studies.account;

public record Transaction(String id, Type type, String accountId, String counterpartyId, long amountCents) {

    public enum Type {
        DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN
    }
}
