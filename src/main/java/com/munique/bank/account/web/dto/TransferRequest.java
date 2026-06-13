// src/main/java/com/munique/bank/account/web/dto/TransferRequest.java
package com.munique.bank.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransferRequest(

        @NotBlank
        @Schema(example = "00012345", description = "Numero da conta de destino")
        String destinationAccountNumber,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be positive")
        @Digits(integer = 17, fraction = 2)
        @Schema(example = "300.00")
        BigDecimal amount
) {
}
