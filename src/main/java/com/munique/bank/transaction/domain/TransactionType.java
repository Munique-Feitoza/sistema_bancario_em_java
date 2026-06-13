// src/main/java/com/munique/bank/transaction/domain/TransactionType.java
package com.munique.bank.transaction.domain;

/** Natureza de um lancamento no extrato. */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    FEE,
    INTEREST,
    TRANSFER_OUT,
    TRANSFER_IN
}
