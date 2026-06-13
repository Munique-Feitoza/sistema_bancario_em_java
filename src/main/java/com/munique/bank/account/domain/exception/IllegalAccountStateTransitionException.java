// src/main/java/com/munique/bank/account/domain/exception/IllegalAccountStateTransitionException.java
package com.munique.bank.account.domain.exception;

import com.munique.bank.shared.exception.BusinessRuleException;

public class IllegalAccountStateTransitionException extends BusinessRuleException {

    public IllegalAccountStateTransitionException(String message) {
        super(message);
    }
}
