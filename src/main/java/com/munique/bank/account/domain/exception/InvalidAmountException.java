// src/main/java/com/munique/bank/account/domain/exception/InvalidAmountException.java
package com.munique.bank.account.domain.exception;

import com.munique.bank.shared.exception.DomainException;

/** Entrada semanticamente invalida (valor nao-positivo). Mapeado para HTTP 422. */
public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
