// src/main/java/com/munique/bank/account/domain/AccountType.java
package com.munique.bank.account.domain;

import com.munique.bank.account.domain.fee.CheckingFeeStrategy;
import com.munique.bank.account.domain.fee.FeeStrategy;
import com.munique.bank.account.domain.fee.SavingsFeeStrategy;

/**
 * Tipo de conta. Cada tipo conhece a sua {@link FeeStrategy} (a propria enum
 * atua como Factory das estrategias de tarifa).
 */
public enum AccountType {

    CHECKING(new CheckingFeeStrategy()),
    SAVINGS(new SavingsFeeStrategy());

    private final FeeStrategy feeStrategy;

    AccountType(FeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy;
    }

    public FeeStrategy feeStrategy() {
        return feeStrategy;
    }
}
