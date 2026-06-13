// src/test/java/com/munique/bank/iam/infrastructure/security/JwtServiceTest.java
package com.munique.bank.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.munique.bank.iam.application.IssuedToken;
import com.munique.bank.iam.domain.AuthenticatedUser;
import com.munique.bank.iam.domain.Role;
import com.munique.bank.iam.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Testes unitarios do {@link JwtService} (sem Spring): emissao, roundtrip de parse,
 * rejeicao de tokens adulterados e validacao do tamanho minimo do secret.
 */
class JwtServiceTest {

    private static final String SECRET = "uma-chave-secreta-de-teste-com-mais-de-32-bytes!!";
    private static final String ISSUER = "digital-bank-test";
    private static final MockEnvironment ENV = new MockEnvironment(); // sem perfil postgres = nao-producao

    private JwtService newService() {
        return new JwtService(new JwtProperties(SECRET, ISSUER, 60), ENV);
    }

    private User userWithId(long id, String username, Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    @Test
    @DisplayName("issue gera um token nao-vazio")
    void issueGeraTokenNaoVazio() {
        JwtService service = newService();
        User user = userWithId(42L, "alice", Role.CUSTOMER);

        IssuedToken token = service.issue(user);

        assertThat(token).isNotNull();
        assertThat(token.value()).isNotBlank();
        assertThat(token.expiresInSeconds()).isEqualTo(60L * 60);
    }

    @Test
    @DisplayName("parse faz roundtrip e recupera id, username e role corretos")
    void parseFazRoundtrip() {
        JwtService service = newService();
        User user = userWithId(42L, "alice", Role.CUSTOMER);

        IssuedToken token = service.issue(user);
        AuthenticatedUser principal = service.parse(token.value());

        assertThat(principal.id()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.role()).isEqualTo(Role.CUSTOMER);
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("parse preserva a role ADMIN no roundtrip")
    void parsePreservaRoleAdmin() {
        JwtService service = newService();
        User user = userWithId(1L, "admin", Role.ADMIN);

        AuthenticatedUser principal = service.parse(service.issue(user).value());

        assertThat(principal.role()).isEqualTo(Role.ADMIN);
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("o User.register exercita o caminho real de emissao (id nulo sem persistencia)")
    void parseAceitaUserRegistradoComIdNulo() {
        JwtService service = newService();
        User registered = User.register("bob", "hash-bcrypt", "Bob Builder", Role.CUSTOMER);

        // Sem persistencia, getId() e null; o servico nao deve quebrar na emissao.
        IssuedToken token = service.issue(registered);

        assertThat(token.value()).isNotBlank();

        // O claim "uid" fica ausente, logo o parse rejeita por claims malformados.
        assertThatThrownBy(() -> service.parse(token.value()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed token claims");
    }

    @Test
    @DisplayName("parse de token adulterado lanca excecao")
    void parseTokenAdulteradoLancaExcecao() {
        JwtService service = newService();
        User user = userWithId(42L, "alice", Role.CUSTOMER);
        String original = service.issue(user).value();
        // Altera o ultimo caractere da assinatura -> verificacao HMAC falha.
        char last = original.charAt(original.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        String tampered = original.substring(0, original.length() - 1) + replacement;

        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("parse de string aleatoria lanca excecao")
    void parseStringAleatoriaLancaExcecao() {
        JwtService service = newService();

        assertThatThrownBy(() -> service.parse("nao-e-um-jwt-valido"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("parse de token emitido por outro issuer lanca excecao")
    void parseTokenComIssuerErradoLancaExcecao() {
        JwtService issuerService = new JwtService(new JwtProperties(SECRET, "outro-issuer", 60), ENV);
        JwtService verifier = newService();
        User user = userWithId(42L, "alice", Role.CUSTOMER);
        String foreignToken = issuerService.issue(user).value();

        assertThatThrownBy(() -> verifier.parse(foreignToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("construir JwtService com secret menor que 32 bytes lanca IllegalStateException")
    void secretCurtoLancaIllegalStateException() {
        JwtProperties shortSecret = new JwtProperties("curto", ISSUER, 60);

        assertThatThrownBy(() -> new JwtService(shortSecret, ENV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
