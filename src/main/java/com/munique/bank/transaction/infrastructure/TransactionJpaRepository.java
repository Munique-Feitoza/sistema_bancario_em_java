// src/main/java/com/munique/bank/transaction/infrastructure/TransactionJpaRepository.java
package com.munique.bank.transaction.infrastructure;

import com.munique.bank.transaction.domain.Transaction;
import com.munique.bank.transaction.domain.repository.TransactionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Adapter JPA do {@link TransactionRepository}. */
@Repository
public interface TransactionJpaRepository extends TransactionRepository, JpaRepository<Transaction, Long> {
}
