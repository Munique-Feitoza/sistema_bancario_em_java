// src/main/java/com/munique/bank/shared/exception/EntityNotFoundException.java
package com.munique.bank.shared.exception;

/** Recurso inexistente. Mapeado para HTTP 404. */
public abstract class EntityNotFoundException extends DomainException {

    protected EntityNotFoundException(String message) {
        super(message);
    }
}
