// src/main/java/com/munique/bank/DigitalBankApplication.java
package com.munique.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Ponto de entrada da Digital Bank API.
 *
 * <p>Arquitetura: DDD + Hexagonal-lite, organizada por contextos delimitados
 * (bounded contexts): {@code account}, {@code transaction} e {@code iam}.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableTransactionManagement
public class DigitalBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalBankApplication.class, args);
    }
}
