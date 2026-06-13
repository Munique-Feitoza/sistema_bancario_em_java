// src/main/java/com/munique/bank/account/domain/exception/InsufficientFundsException.java
package com.munique.bank.account.domain.exception;

import com.munique.bank.shared.exception.BusinessRuleException;

public class InsufficientFundsException extends BusinessRuleException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
