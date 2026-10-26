// src/test/java/com/munique/bank/account/ConcurrentWithdrawalIT.java
package com.munique.bank.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.munique.bank.account.application.AccountService;
import com.munique.bank.account.domain.Account;
import com.munique.bank.account.domain.AccountType;
import com.munique.bank.account.domain.exception.InsufficientFundsException;
import com.munique.bank.account.domain.repository.AccountRepository;
import com.munique.bank.iam.application.AuthService;
import com.munique.bank.iam.domain.AuthenticatedUser;
import com.munique.bank.iam.domain.Role;
import com.munique.bank.iam.domain.User;
import com.munique.bank.shared.domain.Money;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Teste de concorrencia (a "joia da coroa"). Prova, sob carga concorrente real,
 * que o caminho de saque e serializado pelo lock pessimista
 * ({@code AccountRepository#findByIdForUpdate} = SELECT ... FOR UPDATE) dentro
 * do limite transacional de {@link AccountService#withdraw}, impedindo
 * <em>lost update</em> e overdraft.
 *
 * <p>Cenario deterministico: saldo inicial 1000.00 numa conta CHECKING (tarifa
 * de saque = 1.00). Cada saque de 50.00 custa 51.00 no total; com 1000.00 cabem
 * no maximo {@code floor(1000 / 51) = 19} saques. Disparamos 20 saques
 * concorrentes, liberados simultaneamente por uma barreira: exatamente 19
 * sucedem e 1 falha com {@link InsufficientFundsException}. O saldo final nunca
 * fica negativo e e exatamente {@code 1000 - sucessos * 51 = 31.00}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
// Pool de conexoes >= numero de saques concorrentes: todas as transacoes podem
// segurar uma conexao ao mesmo tempo, isolando o teste para a serializacao do
// lock (e nao para a contencao do pool).
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=24",
        "spring.datasource.hikari.connection-timeout=15000"
})
@DisplayName("Saques concorrentes: lock pessimista serializa e impede overdraft / lost update")
class ConcurrentWithdrawalIT {

    private static final int CONCURRENT_WITHDRAWALS = 20;
    private static final int EXPECTED_SUCCESSES = 19; // floor(1000 / 51)

    private static final Money INITIAL_BALANCE = Money.of("1000.00");
    private static final Money WITHDRAWAL_AMOUNT = Money.of("50.00");
    private static final Money COST_PER_WITHDRAWAL = Money.of("51.00"); // 50.00 + 1.00 de tarifa

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("20 saques concorrentes de 50.00 sobre saldo 1000.00: 19 sucessos, saldo final 31.00, nunca negativo")
    void concurrentWithdrawalsAreSerializedAndNeverOverdraft() throws InterruptedException {
        // --- Setup: usuario unico + conta CHECKING com saldo inicial conhecido ---
        AuthenticatedUser owner = registerOwner();
        Account opened = accountService.openAccount(owner, AccountType.CHECKING, "Concurrency Owner", INITIAL_BALANCE);
        Long accountId = opened.getId();

        // --- Orquestracao da corrida ---
        // O pool tem o MESMO tamanho do numero de tarefas: assim todas iniciam,
        // contam o readyGate ate zero e so entao a barreira (startGate) e aberta,
        // disparando os saques verdadeiramente em paralelo.
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WITHDRAWALS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch readyGate = new CountDownLatch(CONCURRENT_WITHDRAWALS);
        CountDownLatch doneGate = new CountDownLatch(CONCURRENT_WITHDRAWALS);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficientFunds = new AtomicInteger();
        AtomicInteger unexpectedFailures = new AtomicInteger();

        try {
            for (int i = 0; i < CONCURRENT_WITHDRAWALS; i++) {
                executor.execute(() -> {
                    readyGate.countDown();
                    try {
                        startGate.await(); // libera todas as threads de uma vez
                        accountService.withdraw(accountId, owner, WITHDRAWAL_AMOUNT);
                        successes.incrementAndGet();
                    } catch (InsufficientFundsException expected) {
                        insufficientFunds.incrementAndGet(); // falha de negocio esperada
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        unexpectedFailures.incrementAndGet();
                    } catch (RuntimeException unexpected) {
                        unexpectedFailures.incrementAndGet();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            // Todas as tasks devem estar prontas (na barreira) antes do disparo.
            assertThat(readyGate.await(10, TimeUnit.SECONDS))
                    .as("todas as %d tasks devem estar prontas na barreira antes do disparo", CONCURRENT_WITHDRAWALS)
                    .isTrue();

            startGate.countDown(); // dispara todos os saques juntos

            assertThat(doneGate.await(30, TimeUnit.SECONDS))
                    .as("todos os saques concorrentes devem concluir dentro do timeout")
                    .isTrue();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("o executor deve encerrar de forma limpa")
                    .isTrue();
        }

        // --- Asserts: nenhuma surpresa nas threads ---
        assertThat(unexpectedFailures.get())
                .as("nenhuma excecao inesperada deve ocorrer nas tasks")
                .isZero();
        assertThat(successes.get())
                .as("o lock pessimista serializa os saques: exatamente floor(1000/51) sucessos")
                .isEqualTo(EXPECTED_SUCCESSES);
        assertThat(insufficientFunds.get())
                .as("os saques excedentes devem falhar com saldo insuficiente, sem overdraft")
                .isEqualTo(CONCURRENT_WITHDRAWALS - EXPECTED_SUCCESSES);

        // --- Asserts: estado persistido (recarrega do repositorio) ---
        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        Money expectedBalance = INITIAL_BALANCE.subtract(
                COST_PER_WITHDRAWAL.multiply(java.math.BigDecimal.valueOf(successes.get())));

        assertThat(reloaded.getBalance().isNegative())
                .as("invariante critica: o saldo NUNCA pode ficar negativo")
                .isFalse();
        assertThat(reloaded.getBalance())
                .as("saldo final = 1000 - (sucessos * 51.00), sem lost update")
                .isEqualTo(expectedBalance)
                .isEqualTo(Money.of("31.00"));
    }

    private AuthenticatedUser registerOwner() {
        String username = "conc-" + UUID.randomUUID();
        User user = authService.register(username, "test-" + UUID.randomUUID(), "Concurrency Owner");
        return new AuthenticatedUser(user.getId(), user.getUsername(), Role.CUSTOMER);
    }
}
