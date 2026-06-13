// src/test/java/com/munique/bank/account/domain/fee/FeeStrategyTest.java
package com.munique.bank.account.domain.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.munique.bank.account.domain.AccountType;
import com.munique.bank.shared.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FeeStrategy: politicas de tarifa por tipo de conta")
class FeeStrategyTest {

    @Nested
    @DisplayName("CheckingFeeStrategy (Conta Corrente)")
    class Checking {

        private final CheckingFeeStrategy strategy = new CheckingFeeStrategy();

        @Test
        @DisplayName("cobra tarifa de saque fixa de 1.00 independente do valor sacado")
        void cobraTarifaDeSaqueFixaIndependenteDoValor() {
            assertThat(strategy.withdrawalFee(Money.of("10.00"))).isEqualTo(Money.of("1.00"));
            assertThat(strategy.withdrawalFee(Money.of("1000.00"))).isEqualTo(Money.of("1.00"));
            assertThat(strategy.withdrawalFee(Money.zero())).isEqualTo(Money.of("1.00"));
        }

        @Test
        @DisplayName("cobra tarifa de manutencao mensal de 20.00")
        void cobraTarifaDeManutencao() {
            assertThat(strategy.maintenanceFee()).isEqualTo(Money.of("20.00"));
        }

        @Test
        @DisplayName("nao oferece rendimento mensal (taxa zero)")
        void naoOfereceRendimento() {
            assertThat(strategy.monthlyInterestRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("SavingsFeeStrategy (Conta Poupanca)")
    class Savings {

        private final SavingsFeeStrategy strategy = new SavingsFeeStrategy();

        @Test
        @DisplayName("e isenta de tarifa de saque (zero) independente do valor")
        void isentaDeTarifaDeSaque() {
            assertThat(strategy.withdrawalFee(Money.of("10.00"))).isEqualTo(Money.zero());
            assertThat(strategy.withdrawalFee(Money.of("1000.00"))).isEqualTo(Money.zero());
        }

        @Test
        @DisplayName("e isenta de tarifa de manutencao (zero)")
        void isentaDeTarifaDeManutencao() {
            assertThat(strategy.maintenanceFee()).isEqualTo(Money.zero());
        }

        @Test
        @DisplayName("oferece rendimento mensal de 0.005 (0,5% a.m.)")
        void ofereceRendimentoMensal() {
            assertThat(strategy.monthlyInterestRate()).isEqualByComparingTo(new BigDecimal("0.005"));
        }
    }

    @Nested
    @DisplayName("FeeStrategyFactory e AccountType.feeStrategy()")
    class Factory {

        @Test
        @DisplayName("forType(CHECKING) resolve uma CheckingFeeStrategy")
        void forTypeCheckingResolveCheckingStrategy() {
            assertThat(FeeStrategyFactory.forType(AccountType.CHECKING))
                    .isInstanceOf(CheckingFeeStrategy.class);
        }

        @Test
        @DisplayName("forType(SAVINGS) resolve uma SavingsFeeStrategy")
        void forTypeSavingsResolveSavingsStrategy() {
            assertThat(FeeStrategyFactory.forType(AccountType.SAVINGS))
                    .isInstanceOf(SavingsFeeStrategy.class);
        }

        @Test
        @DisplayName("AccountType.CHECKING.feeStrategy() devolve a estrategia de Conta Corrente")
        void accountTypeCheckingDevolveCheckingStrategy() {
            assertThat(AccountType.CHECKING.feeStrategy())
                    .isInstanceOf(CheckingFeeStrategy.class);
        }

        @Test
        @DisplayName("AccountType.SAVINGS.feeStrategy() devolve a estrategia de Conta Poupanca")
        void accountTypeSavingsDevolveSavingsStrategy() {
            assertThat(AccountType.SAVINGS.feeStrategy())
                    .isInstanceOf(SavingsFeeStrategy.class);
        }

        @Test
        @DisplayName("forType delega para AccountType.feeStrategy() retornando a mesma instancia")
        void forTypeDelegaParaAccountTypeFeeStrategy() {
            assertThat(FeeStrategyFactory.forType(AccountType.CHECKING))
                    .isSameAs(AccountType.CHECKING.feeStrategy());
            assertThat(FeeStrategyFactory.forType(AccountType.SAVINGS))
                    .isSameAs(AccountType.SAVINGS.feeStrategy());
        }
    }
}
