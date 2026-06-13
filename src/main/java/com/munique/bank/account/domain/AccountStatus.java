// src/main/java/com/munique/bank/account/domain/AccountStatus.java
package com.munique.bank.account.domain;

/**
 * Estado da conta (State pattern). Cada estado decide quais operacoes sao
 * permitidas, blindando o agregado contra transicoes invalidas.
 *
 * <ul>
 *   <li>{@code ACTIVE}: debita e credita.</li>
 *   <li>{@code BLOCKED}: recebe credito, mas nao permite debito (conta congelada).</li>
 *   <li>{@code CLOSED}: nenhuma movimentacao.</li>
 * </ul>
 */
public enum AccountStatus {

    ACTIVE {
        @Override
        public boolean allowsDebit() {
            return true;
        }

        @Override
        public boolean allowsCredit() {
            return true;
        }
    },
    BLOCKED {
        @Override
        public boolean allowsDebit() {
            return false;
        }

        @Override
        public boolean allowsCredit() {
            return true;
        }
    },
    CLOSED {
        @Override
        public boolean allowsDebit() {
            return false;
        }

        @Override
        public boolean allowsCredit() {
            return false;
        }
    };

    public abstract boolean allowsDebit();

    public abstract boolean allowsCredit();

    public boolean isClosed() {
        return this == CLOSED;
    }
}
