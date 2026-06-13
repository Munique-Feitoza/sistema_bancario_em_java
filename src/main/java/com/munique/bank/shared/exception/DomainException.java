// src/main/java/com/munique/bank/shared/exception/DomainException.java
package com.munique.bank.shared.exception;

/**
 * Raiz da hierarquia de excecoes de negocio. Excecoes de dominio nunca carregam
 * stack traces ou detalhes internos para a borda HTTP — apenas uma mensagem
 * segura e tratavel pelo cliente.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
