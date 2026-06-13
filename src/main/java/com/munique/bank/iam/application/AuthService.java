// src/main/java/com/munique/bank/iam/application/AuthService.java
package com.munique.bank.iam.application;

import com.munique.bank.iam.domain.Role;
import com.munique.bank.iam.domain.User;
import com.munique.bank.iam.domain.exception.InvalidCredentialsException;
import com.munique.bank.iam.domain.exception.UserNotFoundException;
import com.munique.bank.iam.domain.exception.UsernameAlreadyExistsException;
import com.munique.bank.iam.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de identidade: cadastro e autenticacao. */
@Service
public class AuthService {

    /**
     * Hash BCrypt descartavel usado para equalizar o tempo de resposta quando o
     * usuario nao existe, mitigando ataques de timing / enumeracao de usuarios.
     */
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa8Hk8m6m3oN3z2yqVqQ1bQ0t9p7r9Vi";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public User register(String username, String rawPassword, String fullName) {
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = User.register(username, passwordHash, fullName, Role.CUSTOMER);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthenticationResult authenticate(String username, String rawPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH); // tempo constante
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        IssuedToken token = tokenService.issue(user);
        return new AuthenticationResult(user, token);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
