// src/main/java/com/munique/bank/iam/infrastructure/UserJpaRepository.java
package com.munique.bank.iam.infrastructure;

import com.munique.bank.iam.domain.User;
import com.munique.bank.iam.domain.repository.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Adapter JPA do {@link UserRepository}. */
@Repository
public interface UserJpaRepository extends UserRepository, JpaRepository<User, Long> {
}
