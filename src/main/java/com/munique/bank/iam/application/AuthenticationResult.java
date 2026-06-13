// src/main/java/com/munique/bank/iam/application/AuthenticationResult.java
package com.munique.bank.iam.application;

import com.munique.bank.iam.domain.User;

/** Resultado de uma autenticacao bem-sucedida: usuario + token emitido. */
public record AuthenticationResult(User user, IssuedToken token) {
}
