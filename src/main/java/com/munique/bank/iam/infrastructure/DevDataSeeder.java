// src/main/java/com/munique/bank/iam/infrastructure/DevDataSeeder.java
package com.munique.bank.iam.infrastructure;

import com.munique.bank.iam.domain.Role;
import com.munique.bank.iam.domain.User;
import com.munique.bank.iam.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria um usuario ADMIN padrao para facilitar a avaliacao do projeto.
 * Ativo apenas FORA do perfil {@code postgres} (ou seja, em dev/test com H2) —
 * jamais semeia credenciais default em producao.
 */
@Component
@Profile("!postgres")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DevDataSeeder(UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${app.seed.admin.username:admin}") String adminUsername,
                         @Value("${app.seed.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin seed skipped: configure app.seed.admin.password explicitly for local development.");
            return;
        }
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }
        User admin = User.register(
                adminUsername,
                passwordEncoder.encode(adminPassword),
                "System Administrator",
                Role.ADMIN);
        userRepository.save(admin);
        log.warn("Seeded default ADMIN user '{}' (dev/H2 only). Disable in production.", adminUsername);
    }
}
