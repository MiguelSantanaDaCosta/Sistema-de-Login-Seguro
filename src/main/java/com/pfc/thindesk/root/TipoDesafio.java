package com.pfc.thindesk.root;

import com.pfc.thindesk.entity.Role;

/**
 * Os desafios de xadrez da aplicação. Todos: puzzles SEGUIDOS (um erro reinicia), tempo total limitado,
 * estado só no servidor e a mesma proteção contra bots/IAs (BotGuard).
 */
public enum TipoDesafio {

    /** Anônimo. Vencer dá a sessão do usuário "root". */
    RAINHA("rainha", "Desafio da Rainha Vermelha", "\u265B", "#ff2b3d", "thindesk_root", "/api/root/desafio", "/root", null,
            false),
    /** Exige login. Vencer promove o usuário a TÉCNICO. */
    CAVALO("cavalo", "Desafio do Cavalo Verde", "\u265E", "#2ecc71", "thindesk_desafio_cavalo", "/api/desafio/cavalo", "/",
            Role.ROLE_TECNICO, true),
    /** Exige login. Vencer promove o usuário a ADMIN. */
    TORRE("torre", "Desafio da Torre Azul", "\u265C", "#3b82f6", "thindesk_desafio_torre", "/api/desafio/torre", "/",
            Role.ROLE_ADMIN, true);

    public final String slug;
    public final String titulo;
    public final String icone;
    public final String cor;
    public final String cookie;
    public final String apiBase;
    public final String urlSucesso;
    /** Perfil concedido ao vencer (null = sessão root). */
    public final Role concede;
    public final boolean exigeLogin;

    TipoDesafio(String slug, String titulo, String icone, String cor, String cookie, String apiBase, String urlSucesso,
            Role concede, boolean exigeLogin) {
        this.slug = slug;
        this.titulo = titulo;
        this.icone = icone;
        this.cor = cor;
        this.cookie = cookie;
        this.apiBase = apiBase;
        this.urlSucesso = urlSucesso;
        this.concede = concede;
        this.exigeLogin = exigeLogin;
    }

    public static TipoDesafio doSlug(String slug) {
        for (TipoDesafio t : values()) {
            if (t.slug.equals(slug) && t != RAINHA) {
                return t;
            }
        }
        return null;
    }
}
