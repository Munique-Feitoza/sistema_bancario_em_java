// src/main/java/com/munique/bank/account/domain/MonthlyClosing.java
package com.munique.bank.account.domain;

import com.munique.bank.shared.domain.Money;

/** Resultado de um fechamento mensal: rendimento creditado e tarifa de manutencao cobrada. */
public record MonthlyClosing(Money interestCredited, Money maintenanceFeeCharged) {
}
