// src/main/java/com/munique/bank/iam/domain/exception/InvalidCredentialsException.java
package com.munique.bank.iam.domain.exception;

import com.munique.bank.shared.exception.AuthenticationFailedException;

/**
 * Mensagem deliberadamente generica para nao permitir enumeracao de usuarios
 * (nao revela se foi o usuario ou a senha que estava errado).
 */
public class InvalidCredentialsException extends AuthenticationFailedException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
