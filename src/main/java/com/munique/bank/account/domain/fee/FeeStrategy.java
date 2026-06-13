// src/main/java/com/munique/bank/account/domain/fee/FeeStrategy.java
package com.munique.bank.account.domain.fee;

import com.munique.bank.shared.domain.Money;
import java.math.BigDecimal;

/**
 * Strategy (GoF) para as politicas de tarifa por tipo de conta. Implementacoes
 * sao stateless e seguras para uso concorrente (singletons).
 */
public interface FeeStrategy {

    /** Tarifa cobrada sobre um saque, em funcao do valor sacado. */
    Money withdrawalFee(Money amount);

    /** Tarifa de manutencao mensal da conta. */
    Money maintenanceFee();

    /** Taxa de rendimento mensal (ex.: 0.005 = 0,5% a.m.). Zero quando nao ha. */
    BigDecimal monthlyInterestRate();
}
