// src/main/java/com/munique/bank/transaction/domain/Transaction.java
package com.munique.bank.transaction.domain;

import com.munique.bank.shared.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Registro imutavel de um lancamento contabil (event sourcing leve / livro-razao).
 * Nao possui setters: uma vez criado, um lancamento nunca muda — auditabilidade.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "counterparty_account_id")
    private Long counterpartyAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Embedded
    @AttributeOverride(name = "amount",
            column = @Column(name = "amount", nullable = false, precision = 19, scale = 2))
    private Money amount;

    @Embedded
    @AttributeOverride(name = "amount",
            column = @Column(name = "balance_after", nullable = false, precision = 19, scale = 2))
    private Money balanceAfter;

    @Column(nullable = false, length = 140)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    private Transaction(Long accountId,
                        Long counterpartyAccountId,
                        TransactionType type,
                        Money amount,
                        Money balanceAfter,
                        String description) {
        this.accountId = Objects.requireNonNull(accountId);
        this.counterpartyAccountId = counterpartyAccountId;
        this.type = Objects.requireNonNull(type);
        this.amount = Objects.requireNonNull(amount);
        this.balanceAfter = Objects.requireNonNull(balanceAfter);
        this.description = description == null ? "" : description;
    }

    public static Transaction deposit(Long accountId, Money amount, Money balanceAfter) {
        return new Transaction(accountId, null, TransactionType.DEPOSIT, amount, balanceAfter, "Deposit");
    }

    public static Transaction withdrawal(Long accountId, Money amount, Money balanceAfter) {
        return new Transaction(accountId, null, TransactionType.WITHDRAWAL, amount, balanceAfter, "Withdrawal");
    }

    public static Transaction fee(Long accountId, Money amount, Money balanceAfter, String description) {
        return new Transaction(accountId, null, TransactionType.FEE, amount, balanceAfter, description);
    }

    public static Transaction interest(Long accountId, Money amount, Money balanceAfter) {
        return new Transaction(accountId, null, TransactionType.INTEREST, amount, balanceAfter, "Monthly interest");
    }

    public static Transaction transferOut(Long accountId, Long counterpartyAccountId, Money amount, Money balanceAfter) {
        return new Transaction(accountId, counterpartyAccountId, TransactionType.TRANSFER_OUT, amount, balanceAfter,
                "Transfer to account " + counterpartyAccountId);
    }

    public static Transaction transferIn(Long accountId, Long counterpartyAccountId, Money amount, Money balanceAfter) {
        return new Transaction(accountId, counterpartyAccountId, TransactionType.TRANSFER_IN, amount, balanceAfter,
                "Transfer from account " + counterpartyAccountId);
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    public TransactionType getType() {
        return type;
    }

    public Money getAmount() {
        return amount;
    }

    public Money getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
