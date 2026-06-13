// src/main/java/com/munique/bank/account/domain/fee/CheckingFeeStrategy.java
package com.munique.bank.account.domain.fee;

import com.munique.bank.shared.domain.Money;
import java.math.BigDecimal;

/** Tarifas de Conta Corrente: cobra saque e manutencao, sem rendimento. */
public final class CheckingFeeStrategy implements FeeStrategy {

    private static final Money WITHDRAWAL_FEE = Money.of("1.00");
    private static final Money MAINTENANCE_FEE = Money.of("20.00");

    @Override
    public Money withdrawalFee(Money amount) {
        return WITHDRAWAL_FEE;
    }

    @Override
    public Money maintenanceFee() {
        return MAINTENANCE_FEE;
    }

    @Override
    public BigDecimal monthlyInterestRate() {
        return BigDecimal.ZERO;
    }
}
