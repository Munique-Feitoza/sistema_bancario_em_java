// src/main/java/com/munique/bank/account/web/dto/TransactionResponse.java
package com.munique.bank.account.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long counterpartyAccountId,
        String description,
        Instant createdAt
) {
}
