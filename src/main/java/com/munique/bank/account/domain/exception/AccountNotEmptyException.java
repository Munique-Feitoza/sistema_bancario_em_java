// src/main/java/com/munique/bank/account/domain/exception/AccountNotEmptyException.java
package com.munique.bank.account.domain.exception;

import com.munique.bank.shared.exception.BusinessRuleException;

public class AccountNotEmptyException extends BusinessRuleException {

    public AccountNotEmptyException() {
        super("Account balance must be zero before it can be closed");
    }
}
