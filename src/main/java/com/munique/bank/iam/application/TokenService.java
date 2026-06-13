// src/main/java/com/munique/bank/iam/application/TokenService.java
package com.munique.bank.iam.application;

import com.munique.bank.iam.domain.User;

/**
 * Port para a emissao de tokens de acesso. A implementacao concreta (JWT) vive
 * na camada de seguranca/infraestrutura — o dominio nao conhece a tecnologia.
 */
public interface TokenService {

    IssuedToken issue(User user);
}
