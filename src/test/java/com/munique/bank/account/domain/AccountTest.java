// src/test/java/com/munique/bank/account/domain/AccountTest.java
package com.munique.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.munique.bank.account.domain.exception.AccountNotActiveException;
import com.munique.bank.account.domain.exception.AccountNotEmptyException;
import com.munique.bank.account.domain.exception.IllegalAccountStateTransitionException;
import com.munique.bank.account.domain.exception.InsufficientFundsException;
import com.munique.bank.account.domain.exception.InvalidAmountException;
import com.munique.bank.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Account (agregado) e AccountStatus")
class AccountTest {

    private static final String AGENCY = "0001";
    private static final String NUMBER = "12345678";
    private static final Long OWNER_ID = 42L;
    private static final String OWNER_NAME = "Munique Feitoza";

    private static Account checking(Money initialDeposit) {
        return Account.open(AGENCY, NUMBER, OWNER_ID, OWNER_NAME, AccountType.CHECKING, initialDeposit);
    }

    private static Account savings(Money initialDeposit) {
        return Account.open(AGENCY, NUMBER, OWNER_ID, OWNER_NAME, AccountType.SAVINGS, initialDeposit);
    }

    @Nested
    @DisplayName("open()")
    class Open {

        @Test
        @DisplayName("cria conta ACTIVE com saldo igual ao deposito inicial")
        void criaContaAtivaComSaldoIgualAoDepositoInicial() {
            Account account = checking(Money.of("100.00"));

            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
            assertThat(account.getAgency()).isEqualTo(AGENCY);
            assertThat(account.getNumber()).isEqualTo(NUMBER);
            assertThat(account.getOwnerId()).isEqualTo(OWNER_ID);
            assertThat(account.getOwnerName()).isEqualTo(OWNER_NAME);
            assertThat(account.getType()).isEqualTo(AccountType.CHECKING);
        }

        @Test
        @DisplayName("deposito inicial nulo abre conta com saldo zero")
        void depositoInicialNuloAbreContaComSaldoZero() {
            Account account = checking(null);

            assertThat(account.getBalance()).isEqualTo(Money.zero());
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("deposito inicial zero e permitido")
        void depositoInicialZeroEPermitido() {
            Account account = checking(Money.zero());

            assertThat(account.getBalance()).isEqualTo(Money.zero());
        }

        @Test
        @DisplayName("deposito inicial negativo lanca InvalidAmountException")
        void depositoInicialNegativoLancaInvalidAmount() {
            assertThatThrownBy(() -> checking(Money.of("-0.01")))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        @DisplayName("campos de texto em branco lancam IllegalArgumentException")
        void camposEmBrancoLancamIllegalArgument() {
            assertThatThrownBy(() ->
                    Account.open(" ", NUMBER, OWNER_ID, OWNER_NAME, AccountType.CHECKING, Money.zero()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                    Account.open(AGENCY, "", OWNER_ID, OWNER_NAME, AccountType.CHECKING, Money.zero()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                    Account.open(AGENCY, NUMBER, OWNER_ID, null, AccountType.CHECKING, Money.zero()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("deposit()")
    class Deposit {

        @Test
        @DisplayName("credita o valor no saldo")
        void creditaValorNoSaldo() {
            Account account = checking(Money.of("100.00"));

            account.deposit(Money.of("50.00"));

            assertThat(account.getBalance()).isEqualTo(Money.of("150.00"));
        }

        @Test
        @DisplayName("valor zero lanca InvalidAmountException")
        void valorZeroLancaInvalidAmount() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.deposit(Money.zero()))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        @DisplayName("valor negativo lanca InvalidAmountException")
        void valorNegativoLancaInvalidAmount() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.deposit(Money.of("-1.00")))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        @DisplayName("nao altera o saldo quando o valor e invalido")
        void naoAlteraSaldoQuandoValorInvalido() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.deposit(Money.zero()))
                    .isInstanceOf(InvalidAmountException.class);
            assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
        }
    }

    @Nested
    @DisplayName("withdraw()")
    class Withdraw {

        @Test
        @DisplayName("CHECKING debita valor + tarifa 1.00 e retorna fee=1.00")
        void checkingDebitaValorMaisTarifaERetornaFee() {
            Account account = checking(Money.of("100.00"));

            Money fee = account.withdraw(Money.of("30.00"));

            assertThat(fee).isEqualTo(Money.of("1.00"));
            assertThat(account.getBalance()).isEqualTo(Money.of("69.00"));
        }

        @Test
        @DisplayName("SAVINGS debita apenas o valor e retorna fee=0")
        void savingsDebitaApenasValorERetornaFeeZero() {
            Account account = savings(Money.of("100.00"));

            Money fee = account.withdraw(Money.of("30.00"));

            assertThat(fee).isEqualTo(Money.zero());
            assertThat(account.getBalance()).isEqualTo(Money.of("70.00"));
        }

        @Test
        @DisplayName("CHECKING com saldo insuficiente considerando a tarifa lanca InsufficientFundsException")
        void checkingSaldoInsuficienteConsiderandoTarifaLanca() {
            Account account = checking(Money.of("100.00"));

            // valor cabe no saldo, mas valor + tarifa (101.00) nao
            assertThatThrownBy(() -> account.withdraw(Money.of("100.00")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
        }

        @Test
        @DisplayName("CHECKING permite sacar valor + tarifa exatamente igual ao saldo")
        void checkingPermiteSacarExatamenteSaldoMenosTarifa() {
            Account account = checking(Money.of("100.00"));

            Money fee = account.withdraw(Money.of("99.00"));

            assertThat(fee).isEqualTo(Money.of("1.00"));
            assertThat(account.getBalance()).isEqualTo(Money.zero());
        }

        @Test
        @DisplayName("SAVINGS com saldo insuficiente lanca InsufficientFundsException")
        void savingsSaldoInsuficienteLanca() {
            Account account = savings(Money.of("50.00"));

            assertThatThrownBy(() -> account.withdraw(Money.of("50.01")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(account.getBalance()).isEqualTo(Money.of("50.00"));
        }

        @Test
        @DisplayName("o saldo nunca fica negativo")
        void saldoNuncaFicaNegativo() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.withdraw(Money.of("100.00")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(account.getBalance().isNegative()).isFalse();
        }

        @Test
        @DisplayName("valor zero lanca InvalidAmountException")
        void valorZeroLancaInvalidAmount() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.withdraw(Money.zero()))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        @DisplayName("valor negativo lanca InvalidAmountException")
        void valorNegativoLancaInvalidAmount() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.withdraw(Money.of("-10.00")))
                    .isInstanceOf(InvalidAmountException.class);
        }
    }

    @Nested
    @DisplayName("debit()")
    class Debit {

        @Test
        @DisplayName("debita sem tarifa")
        void debitaSemTarifa() {
            Account account = checking(Money.of("100.00"));

            account.debit(Money.of("30.00"));

            assertThat(account.getBalance()).isEqualTo(Money.of("70.00"));
        }

        @Test
        @DisplayName("permite debitar exatamente todo o saldo")
        void permiteDebitarTodoOSaldo() {
            Account account = checking(Money.of("100.00"));

            account.debit(Money.of("100.00"));

            assertThat(account.getBalance()).isEqualTo(Money.zero());
        }

        @Test
        @DisplayName("saldo insuficiente lanca InsufficientFundsException")
        void saldoInsuficienteLanca() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.debit(Money.of("100.01")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
        }

        @Test
        @DisplayName("valor zero lanca InvalidAmountException")
        void valorZeroLancaInvalidAmount() {
            Account account = checking(Money.of("100.00"));

            assertThatThrownBy(() -> account.debit(Money.zero()))
                    .isInstanceOf(InvalidAmountException.class);
        }
    }

    @Nested
    @DisplayName("block() / unblock() / close()")
    class StateTransitions {

        @Test
        @DisplayName("BLOCKED nega withdraw com AccountNotActiveException")
        void blockedNegaWithdraw() {
            Account account = checking(Money.of("100.00"));
            account.block();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
            assertThatThrownBy(() -> account.withdraw(Money.of("10.00")))
                    .isInstanceOf(AccountNotActiveException.class);
            assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
        }

        @Test
        @DisplayName("BLOCKED nega debit com AccountNotActiveException")
        void blockedNegaDebit() {
            Account account = checking(Money.of("100.00"));
            account.block();

            assertThatThrownBy(() -> account.debit(Money.of("10.00")))
                    .isInstanceOf(AccountNotActiveException.class);
            assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
        }

        @Test
        @DisplayName("BLOCKED permite deposit (credito)")
        void blockedPermiteDeposit() {
            Account account = checking(Money.of("100.00"));
            account.block();

            account.deposit(Money.of("25.00"));

            assertThat(account.getBalance()).isEqualTo(Money.of("125.00"));
            assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        }

        @Test
        @DisplayName("unblock() volta a conta para ACTIVE e restabelece o debito")
        void unblockVoltaParaActive() {
            Account account = checking(Money.of("100.00"));
            account.block();

            account.unblock();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            account.debit(Money.of("10.00"));
            assertThat(account.getBalance()).isEqualTo(Money.of("90.00"));
        }

        @Test
        @DisplayName("close() so e permitido com saldo zero")
        void closeSoComSaldoZero() {
            Account account = checking(Money.zero());

            account.close();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        @DisplayName("close() com saldo diferente de zero lanca AccountNotEmptyException")
        void closeComSaldoLancaAccountNotEmpty() {
            Account account = checking(Money.of("0.01"));

            assertThatThrownBy(account::close)
                    .isInstanceOf(AccountNotEmptyException.class);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("close() e idempotente em conta ja CLOSED")
        void closeEIdempotente() {
            Account account = checking(Money.zero());
            account.close();

            account.close();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        @DisplayName("block() em conta CLOSED lanca IllegalAccountStateTransitionException")
        void blockEmContaFechadaLanca() {
            Account account = checking(Money.zero());
            account.close();

            assertThatThrownBy(account::block)
                    .isInstanceOf(IllegalAccountStateTransitionException.class);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        @DisplayName("unblock() em conta CLOSED lanca IllegalAccountStateTransitionException")
        void unblockEmContaFechadaLanca() {
            Account account = checking(Money.zero());
            account.close();

            assertThatThrownBy(account::unblock)
                    .isInstanceOf(IllegalAccountStateTransitionException.class);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("isOwnedBy()")
    class Ownership {

        @Test
        @DisplayName("retorna true para o dono")
        void retornaTrueParaODono() {
            Account account = checking(Money.zero());

            assertThat(account.isOwnedBy(OWNER_ID)).isTrue();
        }

        @Test
        @DisplayName("retorna false para outro usuario")
        void retornaFalseParaOutroUsuario() {
            Account account = checking(Money.zero());

            assertThat(account.isOwnedBy(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("AccountStatus")
    class Status {

        @Test
        @DisplayName("ACTIVE permite debito e credito e nao esta fechada")
        void activePermissoes() {
            assertThat(AccountStatus.ACTIVE.allowsDebit()).isTrue();
            assertThat(AccountStatus.ACTIVE.allowsCredit()).isTrue();
            assertThat(AccountStatus.ACTIVE.isClosed()).isFalse();
        }

        @Test
        @DisplayName("BLOCKED permite credito mas nega debito e nao esta fechada")
        void blockedPermissoes() {
            assertThat(AccountStatus.BLOCKED.allowsDebit()).isFalse();
            assertThat(AccountStatus.BLOCKED.allowsCredit()).isTrue();
            assertThat(AccountStatus.BLOCKED.isClosed()).isFalse();
        }

        @Test
        @DisplayName("CLOSED nega debito e credito e esta fechada")
        void closedPermissoes() {
            assertThat(AccountStatus.CLOSED.allowsDebit()).isFalse();
            assertThat(AccountStatus.CLOSED.allowsCredit()).isFalse();
            assertThat(AccountStatus.CLOSED.isClosed()).isTrue();
        }
    }

    @Nested
    @DisplayName("applyMonthlyClosing()")
    class MonthlyClosingTests {

        @Test
        @DisplayName("poupanca credita rendimento de 0,5% a.m. e nao cobra manutencao")
        void poupancaCreditaRendimento() {
            Account account = savings(Money.of("1000.00"));

            MonthlyClosing closing = account.applyMonthlyClosing();

            assertThat(closing.interestCredited()).isEqualTo(Money.of("5.00")); // 1000 * 0.005
            assertThat(closing.maintenanceFeeCharged()).isEqualTo(Money.zero());
            assertThat(account.getBalance()).isEqualTo(Money.of("1005.00"));
        }

        @Test
        @DisplayName("conta corrente cobra tarifa de manutencao e nao rende")
        void contaCorrenteCobraManutencao() {
            Account account = checking(Money.of("1000.00"));

            MonthlyClosing closing = account.applyMonthlyClosing();

            assertThat(closing.interestCredited()).isEqualTo(Money.zero());
            assertThat(closing.maintenanceFeeCharged()).isEqualTo(Money.of("20.00"));
            assertThat(account.getBalance()).isEqualTo(Money.of("980.00"));
        }

        @Test
        @DisplayName("tarifa de manutencao e limitada ao saldo: nunca deixa negativo")
        void tarifaNuncaDeixaNegativo() {
            Account account = checking(Money.of("10.00")); // saldo menor que a tarifa (20.00)

            MonthlyClosing closing = account.applyMonthlyClosing();

            assertThat(closing.maintenanceFeeCharged()).isEqualTo(Money.of("10.00"));
            assertThat(account.getBalance()).isEqualTo(Money.zero());
            assertThat(account.getBalance().isNegative()).isFalse();
        }

        @Test
        @DisplayName("conta encerrada nao aceita fechamento mensal")
        void contaFechadaLanca() {
            Account account = checking(Money.zero());
            account.close();

            assertThatThrownBy(account::applyMonthlyClosing)
                    .isInstanceOf(IllegalAccountStateTransitionException.class);
        }
    }
}
