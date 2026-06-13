// src/main/java/com/munique/bank/account/application/AccountService.java
package com.munique.bank.account.application;

import com.munique.bank.account.domain.Account;
import com.munique.bank.account.domain.AccountType;
import com.munique.bank.account.domain.MonthlyClosing;
import com.munique.bank.account.domain.exception.AccountNotFoundException;
import com.munique.bank.account.domain.repository.AccountRepository;
import com.munique.bank.iam.domain.AuthenticatedUser;
import com.munique.bank.shared.domain.Money;
import com.munique.bank.shared.exception.AccessForbiddenException;
import com.munique.bank.transaction.domain.Transaction;
import com.munique.bank.transaction.domain.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso do contexto de contas. Cada operacao de escrita roda dentro de
 * uma transacao ({@link Transactional}): ou tudo persiste, ou tudo sofre
 * rollback (atomicidade — critico em transferencias).
 */
@Service
public class AccountService {

    private static final String DEFAULT_AGENCY = "0001";
    private static final int MAX_NUMBER_RETRIES = 5;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountNumberGenerator numberGenerator;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          AccountNumberGenerator numberGenerator) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.numberGenerator = numberGenerator;
    }

    @Transactional
    public Account openAccount(AuthenticatedUser requester, AccountType type, String ownerName, Money initialDeposit) {
        Account account = persistWithUniqueNumber(requester.id(), ownerName, type, initialDeposit);
        if (initialDeposit != null && initialDeposit.isPositive()) {
            transactionRepository.save(
                    Transaction.deposit(account.getId(), initialDeposit, account.getBalance()));
        }
        return account;
    }

    private Account persistWithUniqueNumber(Long ownerId, String ownerName, AccountType type, Money initialDeposit) {
        String number = allocateUniqueNumber();
        Account account = Account.open(DEFAULT_AGENCY, number, ownerId, ownerName, type, initialDeposit);
        // Numa corrida rara (dois numeros iguais alocados ao mesmo tempo), a constraint UNIQUE
        // do banco rejeita o INSERT -> DataIntegrityViolationException -> HTTP 409 (cliente repete).
        // O retry NAO pode ficar dentro desta transacao: um flush que falha a marca como
        // rollback-only, inutilizando qualquer tentativa subsequente no mesmo EntityManager.
        return accountRepository.save(account);
    }

    private String allocateUniqueNumber() {
        for (int attempt = 0; attempt < MAX_NUMBER_RETRIES; attempt++) {
            String candidate = numberGenerator.next();
            if (!accountRepository.existsByNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique account number after retries");
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long accountId, AuthenticatedUser requester) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> AccountNotFoundException.withId(accountId));
        authorize(account, requester);
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> listOwnAccounts(AuthenticatedUser requester) {
        return accountRepository.findByOwnerId(requester.id());
    }

    @Transactional
    public Account deposit(Long accountId, AuthenticatedUser requester, Money amount) {
        Account account = lockOrThrow(accountId);
        authorize(account, requester);
        account.deposit(amount);
        Account saved = accountRepository.save(account);
        transactionRepository.save(Transaction.deposit(saved.getId(), amount, saved.getBalance()));
        return saved;
    }

    @Transactional
    public Account withdraw(Long accountId, AuthenticatedUser requester, Money amount) {
        Account account = lockOrThrow(accountId);
        authorize(account, requester);
        Money fee = account.withdraw(amount);
        Account saved = accountRepository.save(account);

        Money balanceAfterAmount = saved.getBalance().add(fee); // saldo antes de descontar a tarifa
        transactionRepository.save(Transaction.withdrawal(saved.getId(), amount, balanceAfterAmount));
        if (fee.isPositive()) {
            transactionRepository.save(Transaction.fee(saved.getId(), fee, saved.getBalance(), "Withdrawal fee"));
        }
        return saved;
    }

    /**
     * Transferencia atomica entre contas. Para evitar DEADLOCK entre
     * transferencias concorrentes em sentidos opostos (A->B e B->A), os locks
     * pessimistas sao adquiridos SEMPRE em ordem crescente de id.
     */
    @Transactional
    public Account transfer(Long sourceAccountId, AuthenticatedUser requester, String destinationNumber, Money amount) {
        Account sourcePreview = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> AccountNotFoundException.withId(sourceAccountId));
        authorize(sourcePreview, requester);

        Account destinationPreview = accountRepository.findByNumber(destinationNumber)
                .orElseThrow(() -> AccountNotFoundException.withNumber(destinationNumber));

        long sourceId = sourceAccountId;
        long destinationId = destinationPreview.getId();
        if (sourceId == destinationId) {
            throw new com.munique.bank.account.domain.exception.SameAccountTransferException();
        }

        long firstId = Math.min(sourceId, destinationId);
        long secondId = Math.max(sourceId, destinationId);
        Account first = lockOrThrow(firstId);
        Account second = lockOrThrow(secondId);

        boolean sourceIsFirst = sourceId == firstId;
        Account source = sourceIsFirst ? first : second;
        Account destination = sourceIsFirst ? second : first;

        // Defesa em profundidade: o lock pessimista serializa as escritas e o @Version
        // (lock otimista) aborta qualquer commit sobre estado obsoleto (-> HTTP 409),
        // de modo que nenhum lost update sobrevive mesmo numa corrida no caminho de transferencia.
        source.debit(amount);
        destination.deposit(amount);
        accountRepository.save(source);
        accountRepository.save(destination);

        transactionRepository.save(
                Transaction.transferOut(source.getId(), destination.getId(), amount, source.getBalance()));
        transactionRepository.save(
                Transaction.transferIn(destination.getId(), source.getId(), amount, destination.getBalance()));
        return source;
    }

    @Transactional
    public Account block(Long accountId, AuthenticatedUser requester) {
        requireAdmin(requester);
        Account account = lockOrThrow(accountId);
        account.block();
        return accountRepository.save(account);
    }

    @Transactional
    public Account unblock(Long accountId, AuthenticatedUser requester) {
        requireAdmin(requester);
        Account account = lockOrThrow(accountId);
        account.unblock();
        return accountRepository.save(account);
    }

    @Transactional
    public Account close(Long accountId, AuthenticatedUser requester) {
        Account account = lockOrThrow(accountId);
        authorize(account, requester); // dono ou admin
        account.close();
        return accountRepository.save(account);
    }

    /**
     * Fechamento mensal (somente ADMIN): credita rendimento e cobra tarifa de
     * manutencao, gravando os lancamentos correspondentes no extrato.
     */
    @Transactional
    public Account applyMonthlyClosing(Long accountId, AuthenticatedUser requester) {
        requireAdmin(requester);
        Account account = lockOrThrow(accountId);
        Money balanceBefore = account.getBalance();

        MonthlyClosing closing = account.applyMonthlyClosing();
        Account saved = accountRepository.save(account);

        if (closing.interestCredited().isPositive()) {
            transactionRepository.save(Transaction.interest(
                    saved.getId(), closing.interestCredited(), balanceBefore.add(closing.interestCredited())));
        }
        if (closing.maintenanceFeeCharged().isPositive()) {
            transactionRepository.save(Transaction.fee(
                    saved.getId(), closing.maintenanceFeeCharged(), saved.getBalance(), "Maintenance fee"));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getStatement(Long accountId, AuthenticatedUser requester, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> AccountNotFoundException.withId(accountId));
        authorize(account, requester);
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    private Account lockOrThrow(long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> AccountNotFoundException.withId(accountId));
    }

    /** Defesa contra IDOR: somente o dono (ou um admin) acessa a conta. */
    private void authorize(Account account, AuthenticatedUser requester) {
        if (requester.isAdmin()) {
            return;
        }
        if (!account.isOwnedBy(requester.id())) {
            throw new AccessForbiddenException("Access to the requested account is forbidden");
        }
    }

    private void requireAdmin(AuthenticatedUser requester) {
        if (!requester.isAdmin()) {
            throw new AccessForbiddenException("This operation requires the ADMIN role");
        }
    }
}
