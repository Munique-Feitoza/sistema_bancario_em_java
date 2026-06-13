// src/main/java/com/munique/bank/account/web/dto/AccountResponse.java
package com.munique.bank.account.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String agency,
        String number,
        String ownerName,
        String type,
        String status,
        BigDecimal balance,
        Instant createdAt
) {
}
