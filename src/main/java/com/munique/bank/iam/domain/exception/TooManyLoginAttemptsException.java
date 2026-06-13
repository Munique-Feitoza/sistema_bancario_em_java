// src/main/java/com/munique/bank/iam/domain/exception/TooManyLoginAttemptsException.java
package com.munique.bank.iam.domain.exception;

import com.munique.bank.shared.exception.DomainException;

/** Excesso de tentativas de login (anti-brute-force). Mapeado para HTTP 429. */
public class TooManyLoginAttemptsException extends DomainException {

    public TooManyLoginAttemptsException() {
        super("Too many failed login attempts. Try again later.");
    }
}
