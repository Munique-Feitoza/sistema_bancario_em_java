// src/main/java/com/munique/bank/iam/web/dto/RegisterRequest.java
package com.munique.bank.iam.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 60)
        @Schema(example = "mario.andrade")
        String username,

        @NotBlank
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        @Schema(example = "S3nh@Forte!2026")
        String password,

        @NotBlank
        @Size(max = 120)
        @Schema(example = "Mario Andrade")
        String fullName
) {
}
