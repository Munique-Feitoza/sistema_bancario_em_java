// src/test/java/com/munique/bank/account/application/AccountServiceTest.java
package com.munique.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.munique.bank.account.domain.Account;
import com.munique.bank.account.domain.AccountType;
import com.munique.bank.account.domain.exception.AccountNotEmptyException;
import com.munique.bank.account.domain.exception.SameAccountTransferException;
import com.munique.bank.account.domain.repository.AccountRepository;
import com.munique.bank.iam.domain.AuthenticatedUser;
import com.munique.bank.iam.domain.Role;
import com.munique.bank.shared.domain.Money;
import com.munique.bank.shared.exception.AccessForbiddenException;
import com.munique.bank.transaction.domain.Transaction;
import com.munique.bank.transaction.domain.TransactionType;
import com.munique.bank.transaction.domain.repository.TransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Testes unitarios do {@link AccountService} com Mockito puro (sem Spring).
 * Cobrem o caminho critico de escrita (deposito/saque/transferencia), a defesa
 * anti-IDOR de autorizacao, o gate de ADMIN em block/unblock e o retry de
 * geracao de numero de conta unico.
 */
class AccountServiceTest {

    private static final long ACCOUNT_ID = 10L;

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private AccountNumberGenerator numberGenerator;
    private AccountService service;

    private final AuthenticatedUser owner = new AuthenticatedUser(1L, "mario", Role.CUSTOMER);
    private final AuthenticatedUser intruder = new AuthenticatedUser(2L, "luigi", Role.CUSTOMER);
    private final AuthenticatedUser admin = new AuthenticatedUser(99L, "admin", Role.ADMIN);

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        numberGenerator = mock(AccountNumberGenerator.class);
        service = new AccountService(accountRepository, transactionRepository, numberGenerator);
    }

    /**
     * Cria um Account real (ACTIVE) pertencente a {@code ownerId}. O id permanece
     * nulo (sem persistencia), portanto so deve ser usado em cenarios que NAO
     * dependem de {@code getId()}.
     */
    private Account openAccountOwnedBy(long ownerId, AccountType type, Money initial) {
        return Account.open("0001", "00000001", ownerId, "Mario", type, initial);
    }

    /** Account mockado: controla id, balance e propriedade — usado onde getId() importa. */
    private Account mockAccount(long id, long ownerId, Money balance) {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(id);
        when(account.getBalance()).thenReturn(balance);
        when(account.isOwnedBy(ownerId)).thenReturn(true);
        return account;
    }

    // ------------------------------------------------------------------
    // getAccount — anti-IDOR
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getAccount retorna a conta quando o requester e o dono")
    void getAccountRetornaParaDono() {
        Account account = openAccountOwnedBy(owner.id(), AccountType.CHECKING, Money.of(100));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        Account result = service.getAccount(ACCOUNT_ID, owner);

        assertThat(result).isSameAs(account);
    }

    @Test
    @DisplayName("getAccount de conta de outro usuario por CUSTOMER lanca AccessForbiddenException (IDOR)")
    void getAccountDeOutroUsuarioLancaForbidden() {
        Account account = openAccountOwnedBy(owner.id(), AccountType.CHECKING, Money.of(100));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.getAccount(ACCOUNT_ID, intruder))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    @DisplayName("getAccount permite ADMIN acessar conta de qualquer usuario")
    void getAccountPermiteAdmin() {
        Account account = openAccountOwnedBy(owner.id(), AccountType.CHECKING, Money.of(100));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThat(service.getAccount(ACCOUNT_ID, admin)).isSameAs(account);
    }

    // ------------------------------------------------------------------
    // deposit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deposit trava a conta, credita o agregado, salva e grava Transaction DEPOSIT")
    void depositCreditaEGravaTransacao() {
        Money amount = Money.of(50);
        Account account = mockAccount(ACCOUNT_ID, owner.id(), Money.of(150));
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        service.deposit(ACCOUNT_ID, owner, amount);

        verify(accountRepository).findByIdForUpdate(ACCOUNT_ID);
        verify(account).deposit(amount);
        verify(accountRepository).save(account);

        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(tx.capture());
        assertThat(tx.getValue().getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(tx.getValue().getAmount()).isEqualTo(amount);
    }

    @Test
    @DisplayName("deposit em conta de outro usuario por CUSTOMER lanca Forbidden e nao muta o agregado")
    void depositDeOutroUsuarioLancaForbidden() {
        Account account = mockAccount(ACCOUNT_ID, owner.id(), Money.of(150));
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.deposit(ACCOUNT_ID, intruder, Money.of(50)))
                .isInstanceOf(AccessForbiddenException.class);

        verify(account, never()).deposit(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("deposit permite ADMIN operar em conta de outro usuario")
    void depositPermiteAdmin() {
        Money amount = Money.of(50);
        Account account = mockAccount(ACCOUNT_ID, owner.id(), Money.of(200));
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        service.deposit(ACCOUNT_ID, admin, amount);

        verify(account).deposit(amount);
        verify(transactionRepository).save(any(Transaction.class));
    }

    // ------------------------------------------------------------------
    // withdraw
    // ------------------------------------------------------------------

    @Test
    @DisplayName("withdraw com tarifa grava WITHDRAWAL e FEE")
    void withdrawComTarifaGravaWithdrawalEFee() {
        Money amount = Money.of(30);
        Money fee = Money.of("1.00");
        // saldo apos saque+tarifa
        Account account = mockAccount(ACCOUNT_ID, owner.id(), Money.of("69.00"));
        when(account.withdraw(amount)).thenReturn(fee);
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        service.withdraw(ACCOUNT_ID, owner, amount);

        verify(account).withdraw(amount);

        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(tx.capture());
        assertThat(tx.getAllValues()).extracting(Transaction::getType)
                .containsExactly(TransactionType.WITHDRAWAL, TransactionType.FEE);
    }

    @Test
    @DisplayName("withdraw sem tarifa (SAVINGS) grava apenas WITHDRAWAL")
    void withdrawSemTarifaGravaApenasWithdrawal() {
        Money amount = Money.of(30);
        Account account = mockAccount(ACCOUNT_ID, owner.id(), Money.of("70.00"));
        when(account.withdraw(amount)).thenReturn(Money.zero());
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        service.withdraw(ACCOUNT_ID, owner, amount);

        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).save(tx.capture());
        assertThat(tx.getValue().getType()).isEqualTo(TransactionType.WITHDRAWAL);
    }

    @Test
    @DisplayName("withdraw em conta de outro usuario por CUSTOMER lanca Forbidden")
    void withdrawDeOutroUsuarioLancaForbidden() {
        Account account = mockAccount(ACCOUNT_ID, owner.id(), Money.of(70));
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdraw(ACCOUNT_ID, intruder, Money.of(30)))
                .isInstanceOf(AccessForbiddenException.class);

        verify(account, never()).withdraw(any());
        verifyNoInteractions(transactionRepository);
    }

    // ------------------------------------------------------------------
    // block / unblock — gate de ADMIN
    // ------------------------------------------------------------------

    @Test
    @DisplayName("block por CUSTOMER lanca AccessForbiddenException e nao toca no repositorio")
    void blockPorCustomerLancaForbidden() {
        assertThatThrownBy(() -> service.block(ACCOUNT_ID, owner))
                .isInstanceOf(AccessForbiddenException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("block por ADMIN chama account.block() e salva")
    void blockPorAdminBloqueiaESalva() {
        Account account = mock(Account.class);
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        service.block(ACCOUNT_ID, admin);

        verify(account).block();
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("unblock por CUSTOMER lanca AccessForbiddenException")
    void unblockPorCustomerLancaForbidden() {
        assertThatThrownBy(() -> service.unblock(ACCOUNT_ID, owner))
                .isInstanceOf(AccessForbiddenException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("unblock por ADMIN chama account.unblock() e salva")
    void unblockPorAdminDesbloqueiaESalva() {
        Account account = mock(Account.class);
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        service.unblock(ACCOUNT_ID, admin);

        verify(account).unblock();
        verify(accountRepository).save(account);
    }

    // ------------------------------------------------------------------
    // transfer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("transfer trava ambas as contas em ordem crescente de id, debita origem, credita destino e grava TRANSFER_OUT/IN")
    void transferTravaAmbasDebitaCreditaEGravaLancamentos() {
        long sourceId = 5L;
        long destId = 9L;
        String destNumber = "00000009";
        Money amount = Money.of(40);

        Account sourcePreview = mockAccount(sourceId, owner.id(), Money.of("60.00"));
        Account source = mockAccount(sourceId, owner.id(), Money.of("60.00"));
        Account destination = mockAccount(destId, intruder.id(), Money.of("140.00"));

        // preview: findById(source) + findByNumber(dest)
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(sourcePreview));
        when(accountRepository.findByNumber(destNumber)).thenReturn(Optional.of(destination));
        // lock em ordem crescente: firstId=5 (source), secondId=9 (dest)
        when(accountRepository.findByIdForUpdate(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(destId)).thenReturn(Optional.of(destination));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(sourceId, owner, destNumber, amount);

        // locks adquiridos em ordem crescente de id (anti-deadlock)
        InOrder locks = inOrder(accountRepository);
        locks.verify(accountRepository).findByIdForUpdate(sourceId);
        locks.verify(accountRepository).findByIdForUpdate(destId);

        verify(source).debit(amount);
        verify(destination).deposit(amount);
        verify(accountRepository).save(source);
        verify(accountRepository).save(destination);

        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(tx.capture());
        assertThat(tx.getAllValues()).extracting(Transaction::getType)
                .containsExactly(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN);

        Transaction out = tx.getAllValues().get(0);
        assertThat(out.getAccountId()).isEqualTo(sourceId);
        assertThat(out.getCounterpartyAccountId()).isEqualTo(destId);
        Transaction in = tx.getAllValues().get(1);
        assertThat(in.getAccountId()).isEqualTo(destId);
        assertThat(in.getCounterpartyAccountId()).isEqualTo(sourceId);
    }

    @Test
    @DisplayName("transfer adquire o lock pela ordem crescente mesmo quando o destino tem id menor que a origem")
    void transferTravaOrdemCrescenteComDestinoMenor() {
        long sourceId = 12L;
        long destId = 4L;
        String destNumber = "00000004";
        Money amount = Money.of(10);

        Account sourcePreview = mockAccount(sourceId, owner.id(), Money.of("100.00"));
        Account source = mockAccount(sourceId, owner.id(), Money.of("100.00"));
        Account destination = mockAccount(destId, intruder.id(), Money.of("0.00"));

        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(sourcePreview));
        when(accountRepository.findByNumber(destNumber)).thenReturn(Optional.of(destination));
        when(accountRepository.findByIdForUpdate(destId)).thenReturn(Optional.of(destination));
        when(accountRepository.findByIdForUpdate(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(sourceId, owner, destNumber, amount);

        // firstId = 4 (dest), secondId = 12 (source)
        InOrder locks = inOrder(accountRepository);
        locks.verify(accountRepository).findByIdForUpdate(destId);
        locks.verify(accountRepository).findByIdForUpdate(sourceId);

        verify(source).debit(amount);
        verify(destination).deposit(amount);
    }

    @Test
    @DisplayName("transfer para a propria conta lanca SameAccountTransferException")
    void transferParaMesmaContaLancaExcecao() {
        long id = 7L;
        String number = "00000007";

        Account sourcePreview = mockAccount(id, owner.id(), Money.of("100.00"));
        Account destinationPreview = mockAccount(id, owner.id(), Money.of("100.00"));

        when(accountRepository.findById(id)).thenReturn(Optional.of(sourcePreview));
        when(accountRepository.findByNumber(number)).thenReturn(Optional.of(destinationPreview));

        assertThatThrownBy(() -> service.transfer(id, owner, number, Money.of(10)))
                .isInstanceOf(SameAccountTransferException.class);

        verify(accountRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("transfer de conta de outro usuario por CUSTOMER lanca Forbidden antes de qualquer lock")
    void transferDeOutroUsuarioLancaForbidden() {
        long sourceId = 5L;
        Account sourcePreview = mockAccount(sourceId, owner.id(), Money.of("100.00"));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(sourcePreview));

        assertThatThrownBy(() -> service.transfer(sourceId, intruder, "00000009", Money.of(10)))
                .isInstanceOf(AccessForbiddenException.class);

        verify(accountRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(transactionRepository);
    }

    // ------------------------------------------------------------------
    // close
    // ------------------------------------------------------------------

    @Test
    @DisplayName("close de conta de outro usuario por CUSTOMER lanca Forbidden e nao fecha nem salva (IDOR)")
    void closeDeOutroUsuarioLancaForbidden() {
        Account account = mock(Account.class);
        when(account.isOwnedBy(intruder.id())).thenReturn(false);
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.close(ACCOUNT_ID, intruder))
                .isInstanceOf(AccessForbiddenException.class);

        verify(account, never()).close();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("close pelo dono fecha a conta e persiste")
    void closePeloDonoFechaEPersiste() {
        Account account = mock(Account.class);
        when(account.isOwnedBy(owner.id())).thenReturn(true);
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        service.close(ACCOUNT_ID, owner);

        verify(account).close();
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("close de conta com saldo positivo propaga AccountNotEmptyException")
    void closeComSaldoPositivoPropagaAccountNotEmpty() {
        Account conta = Account.open("0001", "00000001", owner.id(), "Owner",
                AccountType.CHECKING, Money.of("100.00"));
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(conta));

        assertThatThrownBy(() -> service.close(ACCOUNT_ID, owner))
                .isInstanceOf(AccountNotEmptyException.class);

        verify(accountRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // openAccount
    // ------------------------------------------------------------------

    @Test
    @DisplayName("openAccount tenta um novo numero quando existsByNumber e true (retry do gerador)")
    void openAccountTentaNovoNumeroNaColisao() {
        when(numberGenerator.next()).thenReturn("00000001", "00000002");
        when(accountRepository.existsByNumber("00000001")).thenReturn(true);
        when(accountRepository.existsByNumber("00000002")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.openAccount(owner, AccountType.CHECKING, "Mario", Money.zero());

        verify(numberGenerator, atLeast(2)).next();
        verify(accountRepository).existsByNumber("00000001");
        verify(accountRepository).existsByNumber("00000002");
        assertThat(result.getNumber()).isEqualTo("00000002");
        assertThat(result.getOwnerId()).isEqualTo(owner.id());
        // deposito inicial zero: nenhum lancamento
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("openAccount com deposito inicial positivo grava Transaction DEPOSIT")
    void openAccountComDepositoInicialGravaTransacao() {
        Money initial = Money.of("500.00");
        // Simula o retorno persistido (com id e saldo), como faz o JPA apos o save.
        Account saved = mock(Account.class);
        when(saved.getId()).thenReturn(10L);
        when(saved.getBalance()).thenReturn(initial);
        when(numberGenerator.next()).thenReturn("00000003");
        when(accountRepository.existsByNumber("00000003")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(saved);

        service.openAccount(owner, AccountType.CHECKING, "Mario", initial);

        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(tx.capture());
        assertThat(tx.getValue().getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.getValue().getAmount()).isEqualTo(initial);
        assertThat(tx.getValue().getBalanceAfter()).isEqualTo(initial);
    }

    @Test
    @DisplayName("openAccount usa requester.id() como ownerId")
    void openAccountUsaRequesterComoOwner() {
        when(numberGenerator.next()).thenReturn("00000004");
        when(accountRepository.existsByNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.openAccount(owner, AccountType.SAVINGS, "Mario", Money.zero());

        assertThat(result.isOwnedBy(owner.id())).isTrue();
        assertThat(result.getType()).isEqualTo(AccountType.SAVINGS);
    }
}
