// src/main/java/com/munique/bank/account/domain/fee/FeeStrategyFactory.java
package com.munique.bank.account.domain.fee;

import com.munique.bank.account.domain.AccountType;

/** Factory (GoF) que resolve a {@link FeeStrategy} a partir do tipo de conta. */
public final class FeeStrategyFactory {

    private FeeStrategyFactory() {
    }

    public static FeeStrategy forType(AccountType type) {
        return type.feeStrategy();
    }
}
