// src/test/java/com/munique/bank/iam/application/AuthServiceTest.java
package com.munique.bank.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.munique.bank.iam.domain.Role;
import com.munique.bank.iam.domain.User;
import com.munique.bank.iam.domain.exception.InvalidCredentialsException;
import com.munique.bank.iam.domain.exception.UserNotFoundException;
import com.munique.bank.iam.domain.exception.UsernameAlreadyExistsException;
import com.munique.bank.iam.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String USERNAME = "alice";
    private static final String RAW_PASSWORD = "test-" + java.util.UUID.randomUUID();
    private static final String FULL_NAME = "Alice Liddell";
    private static final String PASSWORD_HASH = "$2a$10$hashFakeParaTeste";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    private static User newCustomer() {
        return User.register(USERNAME, PASSWORD_HASH, FULL_NAME, Role.CUSTOMER);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("lanca UsernameAlreadyExistsException quando o username ja existe")
        void deveLancarQuandoUsernameJaExiste() {
            when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(USERNAME, RAW_PASSWORD, FULL_NAME))
                    .isInstanceOf(UsernameAlreadyExistsException.class)
                    .hasMessageContaining(USERNAME);

            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any());
            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("codifica a senha, salva User com Role.CUSTOMER e retorna o usuario persistido")
        void deveRegistrarNovoUsuario() {
            when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
            User persisted = newCustomer();
            when(userRepository.save(any(User.class))).thenReturn(persisted);

            User result = authService.register(USERNAME, RAW_PASSWORD, FULL_NAME);

            assertThat(result).isSameAs(persisted);

            verify(passwordEncoder).encode(RAW_PASSWORD);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo(USERNAME);
            assertThat(saved.getFullName()).isEqualTo(FULL_NAME);
            assertThat(saved.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
            assertThat(saved.isEnabled()).isTrue();

            verifyNoInteractions(tokenService);
        }
    }

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("usuario inexistente lanca InvalidCredentialsException e ainda invoca matches (tempo constante)")
        void deveLancarEManterTempoConstanteQuandoUsuarioNaoExiste() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.authenticate(USERNAME, RAW_PASSWORD))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder).matches(eq(RAW_PASSWORD), anyString());
            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("senha incorreta lanca InvalidCredentialsException")
        void deveLancarQuandoSenhaIncorreta() {
            User user = newCustomer();
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> authService.authenticate(USERNAME, RAW_PASSWORD))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder).matches(RAW_PASSWORD, PASSWORD_HASH);
            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("usuario desabilitado lanca InvalidCredentialsException")
        void deveLancarQuandoUsuarioDesabilitado() {
            User user = newCustomer();
            user.disable();
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.authenticate(USERNAME, RAW_PASSWORD))
                    .isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("credenciais validas retornam AuthenticationResult com o token emitido pelo TokenService")
        void deveAutenticarComSucesso() {
            User user = newCustomer();
            IssuedToken issued = new IssuedToken("jwt-token-value", 3600L);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(tokenService.issue(user)).thenReturn(issued);

            AuthenticationResult result = authService.authenticate(USERNAME, RAW_PASSWORD);

            assertThat(result.user()).isSameAs(user);
            assertThat(result.token()).isSameAs(issued);
            assertThat(result.token().value()).isEqualTo("jwt-token-value");
            assertThat(result.token().expiresInSeconds()).isEqualTo(3600L);

            verify(tokenService, times(1)).issue(user);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("usuario inexistente lanca UserNotFoundException")
        void deveLancarQuandoUsuarioNaoExiste() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getById(99L))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("99");

            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("retorna o usuario quando existe")
        void deveRetornarUsuarioExistente() {
            User user = newCustomer();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = authService.getById(1L);

            assertThat(result).isSameAs(user);
        }
    }
}
