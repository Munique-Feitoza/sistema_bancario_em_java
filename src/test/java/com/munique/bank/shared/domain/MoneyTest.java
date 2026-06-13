// src/test/java/com/munique/bank/shared/domain/MoneyTest.java
package com.munique.bank.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Money (value object)")
class MoneyTest {

    @Nested
    @DisplayName("Criacao e normalizacao de escala")
    class CriacaoENormalizacao {

        @Test
        @DisplayName("of(BigDecimal) normaliza para escala fixa 2")
        void deveCriarAPartirDeBigDecimalComEscala2() {
            Money money = Money.of(new BigDecimal("10.5"));

            assertThat(money.amount()).isEqualByComparingTo("10.50");
            assertThat(money.amount().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("of(String) normaliza para escala fixa 2")
        void deveCriarAPartirDeStringComEscala2() {
            Money money = Money.of("7.1");

            assertThat(money.amount()).isEqualByComparingTo("7.10");
            assertThat(money.amount().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("of(long) normaliza para escala fixa 2")
        void deveCriarAPartirDeLongComEscala2() {
            Money money = Money.of(42L);

            assertThat(money.amount()).isEqualByComparingTo("42.00");
            assertThat(money.amount().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("zero() retorna valor nulo com escala 2")
        void deveCriarZero() {
            Money money = Money.zero();

            assertThat(money.amount()).isEqualByComparingTo("0.00");
            assertThat(money.amount().scale()).isEqualTo(2);
            assertThat(money.isZero()).isTrue();
        }

        @Test
        @DisplayName("of(long) aceita valor negativo (positividade nao e exigida por Money)")
        void devePermitirConstrucaoNegativa() {
            Money money = Money.of(-5L);

            assertThat(money.amount()).isEqualByComparingTo("-5.00");
            assertThat(money.isNegative()).isTrue();
        }
    }

    @Nested
    @DisplayName("Arredondamento bancario HALF_EVEN")
    class Arredondamento {

        @Test
        @DisplayName("of(\"1.005\") arredonda para 1.00 (para baixo, digito par)")
        void deveArredondarParaBaixoQuandoDigitoAnteriorPar() {
            assertThat(Money.of("1.005").amount()).isEqualByComparingTo("1.00");
        }

        @Test
        @DisplayName("of(\"1.015\") arredonda para 1.02 (para cima, digito par)")
        void deveArredondarParaCimaQuandoDigitoAnteriorImpar() {
            assertThat(Money.of("1.015").amount()).isEqualByComparingTo("1.02");
        }

        @Test
        @DisplayName("of(\"2.675\") arredonda para 2.68 (HALF_EVEN sem erro binario)")
        void deveArredondarMeioParaParAcima() {
            assertThat(Money.of("2.675").amount()).isEqualByComparingTo("2.68");
        }

        @Test
        @DisplayName("of(\"2.665\") arredonda para 2.66 (HALF_EVEN para o par)")
        void deveArredondarMeioParaParAbaixo() {
            assertThat(Money.of("2.665").amount()).isEqualByComparingTo("2.66");
        }
    }

    @Nested
    @DisplayName("Operacoes aritmeticas e imutabilidade")
    class OperacoesEImutabilidade {

        @Test
        @DisplayName("add soma os valores")
        void deveSomar() {
            Money resultado = Money.of("10.00").add(Money.of("2.50"));

            assertThat(resultado.amount()).isEqualByComparingTo("12.50");
        }

        @Test
        @DisplayName("subtract subtrai os valores")
        void deveSubtrair() {
            Money resultado = Money.of("10.00").subtract(Money.of("2.50"));

            assertThat(resultado.amount()).isEqualByComparingTo("7.50");
        }

        @Test
        @DisplayName("subtract pode resultar em valor negativo")
        void deveSubtrairResultandoNegativo() {
            Money resultado = Money.of("2.00").subtract(Money.of("5.00"));

            assertThat(resultado.amount()).isEqualByComparingTo("-3.00");
            assertThat(resultado.isNegative()).isTrue();
        }

        @Test
        @DisplayName("multiply multiplica pelo fator e normaliza escala com HALF_EVEN")
        void deveMultiplicar() {
            Money resultado = Money.of("100.00").multiply(new BigDecimal("0.005"));

            assertThat(resultado.amount()).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("multiply arredonda o produto para escala 2 (HALF_EVEN)")
        void deveMultiplicarComArredondamento() {
            Money resultado = Money.of("10.00").multiply(new BigDecimal("0.333"));

            assertThat(resultado.amount()).isEqualByComparingTo("3.33");
        }

        @Test
        @DisplayName("add retorna nova instancia sem alterar o original")
        void addNaoMutaOriginal() {
            Money original = Money.of("10.00");
            Money other = Money.of("5.00");

            Money resultado = original.add(other);

            assertThat(resultado).isNotSameAs(original);
            assertThat(original.amount()).isEqualByComparingTo("10.00");
            assertThat(other.amount()).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("subtract retorna nova instancia sem alterar o original")
        void subtractNaoMutaOriginal() {
            Money original = Money.of("10.00");
            Money other = Money.of("5.00");

            Money resultado = original.subtract(other);

            assertThat(resultado).isNotSameAs(original);
            assertThat(original.amount()).isEqualByComparingTo("10.00");
            assertThat(other.amount()).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("multiply retorna nova instancia sem alterar o original")
        void multiplyNaoMutaOriginal() {
            Money original = Money.of("10.00");

            Money resultado = original.multiply(new BigDecimal("2"));

            assertThat(resultado).isNotSameAs(original);
            assertThat(original.amount()).isEqualByComparingTo("10.00");
            assertThat(resultado.amount()).isEqualByComparingTo("20.00");
        }
    }

    @Nested
    @DisplayName("Comparacoes de sinal e ordem")
    class Comparacoes {

        @Test
        @DisplayName("isPositive verdadeiro apenas para valores maiores que zero")
        void devePredicarPositividade() {
            assertThat(Money.of("0.01").isPositive()).isTrue();
            assertThat(Money.zero().isPositive()).isFalse();
            assertThat(Money.of("-0.01").isPositive()).isFalse();
        }

        @Test
        @DisplayName("isNegative verdadeiro apenas para valores menores que zero")
        void devePredicarNegatividade() {
            assertThat(Money.of("-0.01").isNegative()).isTrue();
            assertThat(Money.zero().isNegative()).isFalse();
            assertThat(Money.of("0.01").isNegative()).isFalse();
        }

        @Test
        @DisplayName("isZero verdadeiro apenas para valor nulo")
        void devePredicarZero() {
            assertThat(Money.zero().isZero()).isTrue();
            assertThat(Money.of("0.00").isZero()).isTrue();
            assertThat(Money.of("0.01").isZero()).isFalse();
            assertThat(Money.of("-0.01").isZero()).isFalse();
        }

        @Test
        @DisplayName("isGreaterThan compara estritamente maior")
        void devePredicarMaior() {
            assertThat(Money.of("10.00").isGreaterThan(Money.of("9.99"))).isTrue();
            assertThat(Money.of("10.00").isGreaterThan(Money.of("10.00"))).isFalse();
            assertThat(Money.of("10.00").isGreaterThan(Money.of("10.01"))).isFalse();
        }

        @Test
        @DisplayName("isGreaterThanOrEqual compara maior ou igual")
        void devePredicarMaiorOuIgual() {
            assertThat(Money.of("10.00").isGreaterThanOrEqual(Money.of("9.99"))).isTrue();
            assertThat(Money.of("10.00").isGreaterThanOrEqual(Money.of("10.00"))).isTrue();
            assertThat(Money.of("10.00").isGreaterThanOrEqual(Money.of("10.01"))).isFalse();
        }

        @Test
        @DisplayName("isLessThan compara estritamente menor")
        void devePredicarMenor() {
            assertThat(Money.of("9.99").isLessThan(Money.of("10.00"))).isTrue();
            assertThat(Money.of("10.00").isLessThan(Money.of("10.00"))).isFalse();
            assertThat(Money.of("10.01").isLessThan(Money.of("10.00"))).isFalse();
        }
    }

    @Nested
    @DisplayName("equals e hashCode por valor (ignorando escala)")
    class IgualdadePorValor {

        @Test
        @DisplayName("equals true para o mesmo valor com escalas de origem diferentes")
        void equalsIgnoraEscala() {
            assertThat(Money.of("10.00")).isEqualTo(Money.of("10"));
            assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
        }

        @Test
        @DisplayName("hashCode consistente para valores iguais com escalas diferentes")
        void hashCodeConsistenteComEquals() {
            assertThat(Money.of("10.00")).hasSameHashCodeAs(Money.of("10"));
        }

        @Test
        @DisplayName("equals false para valores diferentes")
        void equalsFalseParaValoresDiferentes() {
            assertThat(Money.of("10.00")).isNotEqualTo(Money.of("10.01"));
        }

        @Test
        @DisplayName("equals false contra null e contra tipo distinto")
        void equalsFalseParaNullEOutroTipo() {
            Money money = Money.of("10.00");

            assertThat(money).isNotEqualTo(null);
            assertThat(money).isNotEqualTo("10.00");
        }

        @Test
        @DisplayName("equals reflexivo")
        void equalsReflexivo() {
            Money money = Money.of("10.00");

            assertThat(money).isEqualTo(money);
        }

        @Test
        @DisplayName("zero e of(0) sao iguais por valor")
        void zeroIgualAOfZero() {
            assertThat(Money.zero()).isEqualTo(Money.of("0.00"));
            assertThat(Money.zero()).isEqualTo(Money.of(0L));
        }
    }
}
