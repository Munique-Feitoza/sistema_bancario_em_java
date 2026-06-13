// src/main/java/com/munique/bank/iam/application/IssuedToken.java
package com.munique.bank.iam.application;

/** Token emitido + tempo de expiracao (em segundos), pronto para a resposta da API. */
public record IssuedToken(String value, long expiresInSeconds) {
}
