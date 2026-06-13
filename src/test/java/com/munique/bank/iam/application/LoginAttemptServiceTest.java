// src/test/java/com/munique/bank/iam/application/LoginAttemptServiceTest.java
package com.munique.bank.iam.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.munique.bank.iam.domain.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginAttemptService (anti-brute-force)")
class LoginAttemptServiceTest {

    @Test
    @DisplayName("bloqueia (429) ao atingir o limite de tentativas; abaixo dele nao bloqueia")
    void bloqueiaAposExcederLimite() {
        LoginAttemptService service = new LoginAttemptService(3, 15);
        String key = "mario|127.0.0.1";

        service.onFailure(key);
        service.onFailure(key);
        assertThatCode(() -> service.assertNotBlocked(key)).doesNotThrowAnyException(); // 2 < 3

        service.onFailure(key); // 3a falha atinge o limite
        assertThatThrownBy(() -> service.assertNotBlocked(key))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("login bem-sucedido limpa o contador")
    void sucessoLimpaContador() {
        LoginAttemptService service = new LoginAttemptService(3, 15);
        String key = "mario|127.0.0.1";

        service.onFailure(key);
        service.onFailure(key);
        service.onFailure(key);
        service.onSuccess(key);

        assertThatCode(() -> service.assertNotBlocked(key)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o bloqueio e por chave (username+IP) — chaves distintas nao interferem")
    void chavesIndependentes() {
        LoginAttemptService service = new LoginAttemptService(2, 15);

        service.onFailure("mario|10.0.0.1");
        service.onFailure("mario|10.0.0.1");

        assertThatThrownBy(() -> service.assertNotBlocked("mario|10.0.0.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> service.assertNotBlocked("mario|10.0.0.2")).doesNotThrowAnyException();
    }
}
