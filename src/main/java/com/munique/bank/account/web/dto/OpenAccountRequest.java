// src/main/java/com/munique/bank/account/web/dto/OpenAccountRequest.java
package com.munique.bank.account.web.dto;

import com.munique.bank.account.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record OpenAccountRequest(

        @NotNull
        @Schema(example = "CHECKING")
        AccountType type,

        @NotBlank
        @Size(max = 120)
        @Schema(example = "Mario Andrade")
        String ownerName,

        @DecimalMin(value = "0.00", message = "initialDeposit must not be negative")
        @Digits(integer = 17, fraction = 2)
        @Schema(example = "1000.00", description = "Deposito inicial opcional (default 0)")
        BigDecimal initialDeposit
) {
}
