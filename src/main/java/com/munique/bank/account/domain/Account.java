// src/main/java/com/munique/bank/account/domain/Account.java
package com.munique.bank.account.domain;

import com.munique.bank.account.domain.exception.AccountNotActiveException;
import com.munique.bank.account.domain.exception.AccountNotEmptyException;
import com.munique.bank.account.domain.exception.IllegalAccountStateTransitionException;
import com.munique.bank.account.domain.exception.InsufficientFundsException;
import com.munique.bank.account.domain.exception.InvalidAmountException;
import com.munique.bank.account.domain.fee.FeeStrategy;
import com.munique.bank.account.domain.fee.FeeStrategyFactory;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Aggregate Root do contexto de contas (DDD). Modelo de dominio RICO: todas as
 * invariantes (saldo nunca negativo, operacoes permitidas por estado, valores
 * positivos) sao garantidas por metodos de negocio — nao ha setters publicos
 * nem modelo anemico.
 *
 * <p>Concorrencia: protegido por lock otimista ({@link Version}) como defesa em
 * profundidade. O caminho critico de escrita usa, alem disso, lock pessimista
 * no repositorio (ver {@code AccountRepository#findByIdForUpdate}).</p>
 */
@Entity
@Table(
        name = "accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounts_number", columnNames = "number")
)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String agency;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Embedded
    @AttributeOverride(name = "amount",
            column = @Column(name = "balance", nullable = false, precision = 19, scale = 2))
    private Money balance;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Construtor exigido pelo JPA. Nao usar diretamente. */
    protected Account() {
    }

    /**
     * Factory method: abre uma nova conta ATIVA. Encapsula as invariantes de
     * criacao (deposito inicial nao-negativo, campos obrigatorios).
     */
    public static Account open(String agency,
                               String number,
                               Long ownerId,
                               String ownerName,
                               AccountType type,
                               Money initialDeposit) {
        Account account = new Account();
        account.agency = requireText(agency, "agency");
        account.number = requireText(number, "number");
        account.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        account.ownerName = requireText(ownerName, "ownerName");
        account.type = Objects.requireNonNull(type, "type must not be null");
        account.status = AccountStatus.ACTIVE;

        Money initial = initialDeposit == null ? Money.zero() : initialDeposit;
        if (initial.isNegative()) {
            throw new InvalidAmountException("Initial deposit must not be negative");
        }
        account.balance = initial;
        return account;
    }

    /** Credita um valor positivo. Usado em depositos e no recebimento de transferencias. */
    public void deposit(Money amount) {
        requirePositive(amount);
        if (!status.allowsCredit()) {
            throw new AccountNotActiveException("Account " + number + " does not allow credit (status " + status + ")");
        }
        this.balance = this.balance.add(amount);
    }

    /**
     * Saque em dinheiro: aplica a tarifa de saque da estrategia do tipo de conta
     * e debita valor + tarifa. Retorna a tarifa efetivamente cobrada (para o
     * lancamento no extrato).
     */
    public Money withdraw(Money amount) {
        requirePositive(amount);
        requireDebitAllowed();
        Money fee = FeeStrategyFactory.forType(type).withdrawalFee(amount);
        Money total = amount.add(fee);
        if (this.balance.isLessThan(total)) {
            throw new InsufficientFundsException(
                    "Insufficient funds in account " + number + ": balance " + balance + " < " + total);
        }
        this.balance = this.balance.subtract(total);
        return fee;
    }

    /** Debito sem tarifa. Usado no envio de transferencias. */
    public void debit(Money amount) {
        requirePositive(amount);
        requireDebitAllowed();
        if (this.balance.isLessThan(amount)) {
            throw new InsufficientFundsException(
                    "Insufficient funds in account " + number + ": balance " + balance + " < " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void block() {
        if (status.isClosed()) {
            throw new IllegalAccountStateTransitionException("Cannot block a closed account");
        }
        this.status = AccountStatus.BLOCKED;
    }

    public void unblock() {
        if (status.isClosed()) {
            throw new IllegalAccountStateTransitionException("Cannot reactivate a closed account");
        }
        this.status = AccountStatus.ACTIVE;
    }

    public void close() {
        if (status.isClosed()) {
            return; // idempotente
        }
        if (!balance.isZero()) {
            throw new AccountNotEmptyException();
        }
        this.status = AccountStatus.CLOSED;
    }

    /**
     * Fechamento mensal: credita o rendimento (poupanca) e cobra a tarifa de
     * manutencao (conta corrente), conforme a {@link FeeStrategy} do tipo de conta.
     * A tarifa nunca leva o saldo a negativo (e limitada ao saldo disponivel).
     */
    public MonthlyClosing applyMonthlyClosing() {
        if (status.isClosed()) {
            throw new IllegalAccountStateTransitionException("Cannot run monthly closing on a closed account");
        }
        FeeStrategy strategy = FeeStrategyFactory.forType(type);

        Money interest = balance.multiply(strategy.monthlyInterestRate());
        if (interest.isPositive()) {
            this.balance = this.balance.add(interest);
        }

        Money fee = strategy.maintenanceFee();
        Money charged = balance.isGreaterThanOrEqual(fee) ? fee : balance; // nunca negativa
        if (charged.isPositive()) {
            this.balance = this.balance.subtract(charged);
        }
        return new MonthlyClosing(interest, charged);
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }

    private void requireDebitAllowed() {
        if (!status.allowsDebit()) {
            throw new AccountNotActiveException("Account " + number + " does not allow debit (status " + status + ")");
        }
    }

    private static void requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new InvalidAmountException("Amount must be a positive value");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public String getAgency() {
        return agency;
    }

    public String getNumber() {
        return number;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public AccountType getType() {
        return type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Money getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
