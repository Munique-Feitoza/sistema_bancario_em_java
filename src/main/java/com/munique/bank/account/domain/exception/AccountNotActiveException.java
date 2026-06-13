// src/main/java/com/munique/bank/account/domain/exception/AccountNotActiveException.java
package com.munique.bank.account.domain.exception;

import com.munique.bank.shared.exception.BusinessRuleException;

public class AccountNotActiveException extends BusinessRuleException {

    public AccountNotActiveException(String message) {
        super(message);
    }
}
