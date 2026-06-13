// src/main/java/com/munique/bank/iam/infrastructure/security/JwtService.java
package com.munique.bank.iam.infrastructure.security;

import com.munique.bank.iam.application.IssuedToken;
import com.munique.bank.iam.application.TokenService;
import com.munique.bank.iam.domain.AuthenticatedUser;
import com.munique.bank.iam.domain.Role;
import com.munique.bank.iam.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Adapter de {@link TokenService} baseado em JWT assinado com HMAC-SHA256
 * (JJWT). Tambem valida tokens recebidos, reconstruindo o principal de seguranca.
 */
@Service
public class JwtService implements TokenService {

    private final SecretKey key;
    private final String issuer;
    private final long expirationMinutes;

    public JwtService(JwtProperties properties, Environment environment) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 bytes for HS256");
        }
        // Fail-fast: nunca subir em producao (perfil postgres) com o segredo default de dev.
        boolean production = environment.matchesProfiles("postgres");
        if (production && properties.secret().toLowerCase().contains("change-me")) {
            throw new IllegalStateException(
                    "Refusing to start in production with a default JWT secret. "
                            + "Set a unique app.security.jwt.secret (env JWT_SECRET, >= 32 bytes).");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = properties.issuer();
        this.expirationMinutes = properties.expirationMinutes();
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expirationMinutes * 60);
    }

    /** Valida assinatura/expiracao/issuer e devolve o principal. Lanca em caso de token invalido. */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Number uid = claims.get("uid", Number.class);
        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        if (uid == null || username == null || role == null) {
            throw new IllegalArgumentException("Malformed token claims");
        }
        return new AuthenticatedUser(uid.longValue(), username, Role.valueOf(role));
    }
}
