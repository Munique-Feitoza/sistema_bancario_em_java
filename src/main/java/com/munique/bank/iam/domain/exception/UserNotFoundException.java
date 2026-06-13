// src/main/java/com/munique/bank/iam/domain/exception/UserNotFoundException.java
package com.munique.bank.iam.domain.exception;

import com.munique.bank.shared.exception.EntityNotFoundException;

public class UserNotFoundException extends EntityNotFoundException {

    public UserNotFoundException(Long id) {
        super("User not found: " + id);
    }
}
