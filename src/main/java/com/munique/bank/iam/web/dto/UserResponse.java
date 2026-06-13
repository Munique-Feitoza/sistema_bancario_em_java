// src/main/java/com/munique/bank/iam/web/dto/UserResponse.java
package com.munique.bank.iam.web.dto;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String role
) {
}
