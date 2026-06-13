// src/main/java/com/munique/bank/iam/domain/AuthenticatedUser.java
package com.munique.bank.iam.domain;

/**
 * Principal de seguranca exposto no SecurityContext apos a validacao do JWT.
 * Carrega o id do usuario, usado nas verificacoes de propriedade (anti-IDOR).
 */
public record AuthenticatedUser(Long id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
