// src/main/java/com/munique/bank/account/infrastructure/AccountJpaRepository.java
package com.munique.bank.account.infrastructure;

import com.munique.bank.account.domain.Account;
import com.munique.bank.account.domain.repository.AccountRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Adapter JPA do {@link AccountRepository}. O Spring Data gera a implementacao;
 * os metodos derivados e o lock pessimista sao declarados aqui.
 */
@Repository
public interface AccountJpaRepository extends AccountRepository, JpaRepository<Account, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Override
    Optional<Account> findByNumber(String number);

    @Override
    boolean existsByNumber(String number);
}
