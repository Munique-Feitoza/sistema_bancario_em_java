// src/main/java/com/munique/bank/account/web/dto/DepositRequest.java
package com.munique.bank.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DepositRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be positive")
        @Digits(integer = 17, fraction = 2)
        @Schema(example = "250.00")
        BigDecimal amount
) {
}
