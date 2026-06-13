// src/main/java/com/munique/bank/account/web/AccountController.java
package com.munique.bank.account.web;

import com.munique.bank.account.application.AccountService;
import com.munique.bank.account.domain.Account;
import com.munique.bank.account.web.dto.AccountResponse;
import com.munique.bank.account.web.dto.DepositRequest;
import com.munique.bank.account.web.dto.OpenAccountRequest;
import com.munique.bank.account.web.dto.TransactionResponse;
import com.munique.bank.account.web.dto.TransferRequest;
import com.munique.bank.account.web.dto.WithdrawRequest;
import com.munique.bank.iam.domain.AuthenticatedUser;
import com.munique.bank.shared.domain.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Abertura de contas e movimentacoes")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre uma conta para o usuario autenticado")
    public AccountResponse open(@AuthenticationPrincipal AuthenticatedUser principal,
                                @Valid @RequestBody OpenAccountRequest request) {
        Money initial = request.initialDeposit() == null ? Money.zero() : Money.of(request.initialDeposit());
        Account account = accountService.openAccount(principal, request.type(), request.ownerName(), initial);
        return AccountMapper.toResponse(account);
    }

    @GetMapping
    @Operation(summary = "Lista as contas do usuario autenticado")
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return accountService.listOwnAccounts(principal).stream()
                .map(AccountMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma conta (apenas dono ou admin)")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser principal,
                               @PathVariable Long id) {
        return AccountMapper.toResponse(accountService.getAccount(id, principal));
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Deposita um valor na conta")
    public AccountResponse deposit(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody DepositRequest request) {
        return AccountMapper.toResponse(accountService.deposit(id, principal, Money.of(request.amount())));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Saca um valor da conta (aplica tarifa conforme o tipo)")
    public AccountResponse withdraw(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody WithdrawRequest request) {
        return AccountMapper.toResponse(accountService.withdraw(id, principal, Money.of(request.amount())));
    }

    @PostMapping("/{id}/transfer")
    @Operation(summary = "Transfere para outra conta de forma atomica")
    public AccountResponse transfer(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody TransferRequest request) {
        Account source = accountService.transfer(
                id, principal, request.destinationAccountNumber(), Money.of(request.amount()));
        return AccountMapper.toResponse(source);
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Extrato paginado da conta")
    public PagedModel<TransactionResponse> statement(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable Long id,
                                                     @PageableDefault(size = 20) Pageable pageable) {
        Page<TransactionResponse> page = accountService.getStatement(id, principal, pageable)
                .map(TransactionMapper::toResponse);
        return new PagedModel<>(page);
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bloqueia a conta (somente ADMIN)")
    public AccountResponse block(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @PathVariable Long id) {
        return AccountMapper.toResponse(accountService.block(id, principal));
    }

    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reativa a conta (somente ADMIN)")
    public AccountResponse unblock(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable Long id) {
        return AccountMapper.toResponse(accountService.unblock(id, principal));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Encerra a conta (dono ou admin; exige saldo zero)")
    public AccountResponse close(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @PathVariable Long id) {
        return AccountMapper.toResponse(accountService.close(id, principal));
    }

    @PostMapping("/{id}/monthly-closing")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Fechamento mensal: credita rendimento e cobra tarifa de manutencao (somente ADMIN)")
    public AccountResponse monthlyClosing(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable Long id) {
        return AccountMapper.toResponse(accountService.applyMonthlyClosing(id, principal));
    }
}
