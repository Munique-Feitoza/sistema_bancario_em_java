// src/main/java/com/munique/bank/iam/domain/exception/UsernameAlreadyExistsException.java
package com.munique.bank.iam.domain.exception;

import com.munique.bank.shared.exception.BusinessRuleException;

public class UsernameAlreadyExistsException extends BusinessRuleException {

    public UsernameAlreadyExistsException(String username) {
        super("Username already taken: " + username);
    }
}
