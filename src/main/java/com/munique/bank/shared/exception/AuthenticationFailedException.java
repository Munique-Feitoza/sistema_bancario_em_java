// src/main/java/com/munique/bank/shared/exception/AuthenticationFailedException.java
package com.munique.bank.shared.exception;

/** Falha de autenticacao (credenciais invalidas). Mapeado para HTTP 401. */
public class AuthenticationFailedException extends DomainException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
