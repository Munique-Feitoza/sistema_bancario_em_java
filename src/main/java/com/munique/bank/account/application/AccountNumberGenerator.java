// src/main/java/com/munique/bank/account/application/AccountNumberGenerator.java
package com.munique.bank.account.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Gera numeros de conta pseudo-aleatorios de 8 digitos com {@link SecureRandom}. */
@Component
public class AccountNumberGenerator {

    private static final int BOUND = 100_000_000; // 8 digitos: 0..99.999.999

    private final SecureRandom random = new SecureRandom();

    public String next() {
        return String.format("%08d", random.nextInt(BOUND));
    }
}
