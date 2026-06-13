// src/main/java/com/munique/bank/account/domain/exception/SameAccountTransferException.java
package com.munique.bank.account.domain.exception;

import com.munique.bank.shared.exception.BusinessRuleException;

public class SameAccountTransferException extends BusinessRuleException {

    public SameAccountTransferException() {
        super("Source and destination accounts must be different");
    }
}
