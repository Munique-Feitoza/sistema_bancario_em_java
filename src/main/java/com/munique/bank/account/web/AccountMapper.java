// src/main/java/com/munique/bank/account/web/AccountMapper.java
package com.munique.bank.account.web;

import com.munique.bank.account.domain.Account;
import com.munique.bank.account.web.dto.AccountResponse;

/** Converte o agregado de dominio em DTO de saida (isola o contrato HTTP do dominio). */
public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAgency(),
                account.getNumber(),
                account.getOwnerName(),
                account.getType().name(),
                account.getStatus().name(),
                account.getBalance().amount(),
                account.getCreatedAt());
    }
}
