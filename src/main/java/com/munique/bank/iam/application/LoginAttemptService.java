// src/main/java/com/munique/bank/iam/application/LoginAttemptService.java
package com.munique.bank.iam.application;

import com.munique.bank.iam.domain.exception.TooManyLoginAttemptsException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Defesa anti-brute-force para o login: conta falhas por chave (username+IP) numa
 * janela deslizante e bloqueia novas tentativas (HTTP 429) ao exceder o limite.
 *
 * <p>Implementacao em memoria (O(1) por operacao) — suficiente para uma instancia.
 * Em producao multi-instancia, o mesmo contrato seria implementado sobre Redis /
 * um token bucket distribuido (ex.: Bucket4j).</p>
 */
@Component
public class LoginAttemptService {

    private record Window(int count, Instant resetAt) {
    }

    private final int maxAttempts;
    private final Duration lockout;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.lockout-minutes:15}") long lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    /** Bloqueia (429) se a chave ja excedeu o limite dentro da janela atual. */
    public void assertNotBlocked(String key) {
        Window window = attempts.get(key);
        if (window != null && window.count() >= maxAttempts && Instant.now().isBefore(window.resetAt())) {
            throw new TooManyLoginAttemptsException();
        }
    }

    /** Registra uma falha; reinicia a janela quando a anterior expirou. */
    public void onFailure(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, window) -> {
            if (window == null || now.isAfter(window.resetAt())) {
                return new Window(1, now.plus(lockout));
            }
            return new Window(window.count() + 1, window.resetAt());
        });
    }

    /** Login bem-sucedido limpa o contador da chave. */
    public void onSuccess(String key) {
        attempts.remove(key);
    }
}
