// src/main/java/com/munique/bank/account/web/TransactionMapper.java
package com.munique.bank.account.web;

import com.munique.bank.account.web.dto.TransactionResponse;
import com.munique.bank.transaction.domain.Transaction;

public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getAmount().amount(),
                transaction.getBalanceAfter().amount(),
                transaction.getCounterpartyAccountId(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
