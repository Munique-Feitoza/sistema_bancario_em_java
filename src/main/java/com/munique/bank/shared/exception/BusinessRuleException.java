// src/main/java/com/munique/bank/shared/exception/BusinessRuleException.java
package com.munique.bank.shared.exception;

/** Violacao de invariante/regra de negocio. Mapeado para HTTP 409 (Conflict). */
public abstract class BusinessRuleException extends DomainException {

    protected BusinessRuleException(String message) {
        super(message);
    }
}
