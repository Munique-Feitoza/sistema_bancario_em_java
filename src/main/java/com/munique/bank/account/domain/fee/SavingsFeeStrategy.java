// src/main/java/com/munique/bank/account/domain/fee/SavingsFeeStrategy.java
package com.munique.bank.account.domain.fee;

import com.munique.bank.shared.domain.Money;
import java.math.BigDecimal;

/** Tarifas de Conta Poupanca: isenta de tarifas, com rendimento mensal. */
public final class SavingsFeeStrategy implements FeeStrategy {

    private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("0.005");

    @Override
    public Money withdrawalFee(Money amount) {
        return Money.zero();
    }

    @Override
    public Money maintenanceFee() {
        return Money.zero();
    }

    @Override
    public BigDecimal monthlyInterestRate() {
        return MONTHLY_INTEREST_RATE;
    }
}
