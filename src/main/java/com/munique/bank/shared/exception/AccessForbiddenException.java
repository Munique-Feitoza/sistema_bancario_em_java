// src/main/java/com/munique/bank/shared/exception/AccessForbiddenException.java
package com.munique.bank.shared.exception;

/**
 * Acesso negado a um recurso pertencente a outro usuario (defesa contra IDOR).
 * Mapeado para HTTP 403.
 */
public class AccessForbiddenException extends DomainException {

    public AccessForbiddenException(String message) {
        super(message);
    }
}
