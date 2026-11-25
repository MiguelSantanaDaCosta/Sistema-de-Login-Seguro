// ============================================================
// Role.java
// Autor: Miguel Santana
// Descrição: Perfis de acesso e a HIERARQUIA entre eles (fonte única de verdade):
//            ROLE_ROOT > ROLE_ADMIN > ROLE_TECNICO > ROLE_USUARIO.
//            O ms-chamados aplica a mesma hierarquia (ver PapelUtil).
// ============================================================
package com.pfc.thindesk.entity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum Role {
    ROLE_ADMIN,
    ROLE_TECNICO,
    ROLE_USUARIO,
    /** Perfil "piada": só se obtém vencendo o desafio da rainha vermelha (ver RootDesafioService). */
    ROLE_ROOT;

    /** Este perfil e todos os que ele implica (ex.: ADMIN => ADMIN, TECNICO, USUARIO). */
    public Set<Role> efetivos() {
        return switch (this) {
            case ROLE_ROOT -> EnumSet.of(ROLE_ROOT, ROLE_ADMIN, ROLE_TECNICO, ROLE_USUARIO);
            case ROLE_ADMIN -> EnumSet.of(ROLE_ADMIN, ROLE_TECNICO, ROLE_USUARIO);
            case ROLE_TECNICO -> EnumSet.of(ROLE_TECNICO, ROLE_USUARIO);
            case ROLE_USUARIO -> EnumSet.of(ROLE_USUARIO);
        };
    }

    /** União dos perfis efetivos de todos os perfis informados. */
    public static Set<Role> expandir(Collection<Role> roles) {
        Set<Role> todos = EnumSet.noneOf(Role.class);
        for (Role r : roles) {
            todos.addAll(r.efetivos());
        }
        return todos;
    }

    /** Nomes dos perfis efetivos, para a claim "roles" do JWT lida pelo ms-chamados. */
    public static List<String> nomesEfetivos(Collection<Role> roles) {
        return expandir(roles).stream().map(Role::name).sorted().toList();
    }
}
