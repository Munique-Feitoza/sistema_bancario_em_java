// src/main/java/com/munique/bank/iam/web/dto/LoginRequest.java
package com.munique.bank.iam.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank
        @Schema(example = "mario.andrade")
        String username,

        @NotBlank
        @Schema(example = "S3nh@Forte!2026")
        String password
) {
}
