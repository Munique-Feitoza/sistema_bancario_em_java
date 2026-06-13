// src/main/java/com/munique/bank/account/domain/repository/AccountRepository.java
package com.munique.bank.account.domain.repository;

import com.munique.bank.account.domain.Account;
import java.util.List;
import java.util.Optional;

/**
 * Port (DDD/Hexagonal) do repositorio do agregado {@link Account}. O dominio
 * depende desta abstracao; o adapter JPA vive na camada de infraestrutura.
 */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(Long id);

    /**
     * Carrega a conta adquirindo um lock pessimista de escrita
     * (SELECT ... FOR UPDATE). Usado no caminho critico (saque/transferencia)
     * para serializar escritas concorrentes e impedir lost update.
     */
    Optional<Account> findByIdForUpdate(Long id);

    Optional<Account> findByNumber(String number);

    List<Account> findByOwnerId(Long ownerId);

    boolean existsByNumber(String number);
}
