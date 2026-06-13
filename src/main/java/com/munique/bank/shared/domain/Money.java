// src/main/java/com/munique/bank/shared/domain/Money.java
package com.munique.bank.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object (DDD) que representa um valor monetario.
 *
 * <p>Imutavel e blindado contra os erros de ponto flutuante do {@code double}
 * (a versao original do projeto usava {@code double} para o saldo — proibido em
 * dominio financeiro). Usa {@link BigDecimal} com escala fixa de 2 casas e
 * arredondamento bancario (HALF_EVEN).</p>
 */
@Embeddable
public class Money implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Construtor exigido pelo JPA. Nao usar diretamente. */
    protected Money() {
    }

    private Money(BigDecimal amount) {
        this.amount = Objects.requireNonNull(amount, "amount must not be null")
                .setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        return amount.compareTo(other.amount) < 0;
    }

    public BigDecimal amount() {
        return amount;
    }

    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        // compareTo ignora diferencas de escala (ja normalizadas no construtor)
        return this.amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
