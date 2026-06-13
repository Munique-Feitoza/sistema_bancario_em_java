// src/main/java/com/munique/bank/iam/infrastructure/security/JwtProperties.java
package com.munique.bank.iam.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades do JWT, vinculadas a partir de {@code app.security.jwt.*}. */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        long expirationMinutes
) {
}
