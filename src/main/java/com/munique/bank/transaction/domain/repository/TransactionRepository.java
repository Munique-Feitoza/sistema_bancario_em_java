// src/main/java/com/munique/bank/transaction/domain/repository/TransactionRepository.java
package com.munique.bank.transaction.domain.repository;

import com.munique.bank.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Port do repositorio de lancamentos (extrato). */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
}
