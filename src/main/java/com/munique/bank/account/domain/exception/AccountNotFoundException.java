// src/main/java/com/munique/bank/account/domain/exception/AccountNotFoundException.java
package com.munique.bank.account.domain.exception;

import com.munique.bank.shared.exception.EntityNotFoundException;

public class AccountNotFoundException extends EntityNotFoundException {

    public AccountNotFoundException(String message) {
        super(message);
    }

    public static AccountNotFoundException withId(Long id) {
        return new AccountNotFoundException("Account not found: " + id);
    }

    public static AccountNotFoundException withNumber(String number) {
        return new AccountNotFoundException("Account not found: " + number);
    }
}
