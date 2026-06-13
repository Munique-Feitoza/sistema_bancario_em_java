// src/main/java/com/munique/bank/iam/domain/Role.java
package com.munique.bank.iam.domain;

/** Papel de autorizacao do usuario. */
public enum Role {
    ADMIN,
    CUSTOMER;

    /** Authority no formato esperado pelo Spring Security ("ROLE_ADMIN"...). */
    public String authority() {
        return "ROLE_" + name();
    }
}
