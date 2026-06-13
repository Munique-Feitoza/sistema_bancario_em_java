// src/main/java/com/munique/bank/iam/domain/repository/UserRepository.java
package com.munique.bank.iam.domain.repository;

import com.munique.bank.iam.domain.User;
import java.util.Optional;

/** Port do repositorio de usuarios. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
