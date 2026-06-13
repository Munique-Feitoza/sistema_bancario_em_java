// src/main/java/com/munique/bank/iam/web/dto/AuthResponse.java
package com.munique.bank.iam.web.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String username,
        String role
) {
    public static AuthResponse bearer(String accessToken, long expiresInSeconds, String username, String role) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, username, role);
    }
}
