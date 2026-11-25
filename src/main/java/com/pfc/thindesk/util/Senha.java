package com.pfc.thindesk.util;

import java.nio.charset.StandardCharsets;

/** O limite do BCrypt é em bytes, inclusive para senhas com acentos. */
public final class Senha {
    private Senha() {
    }

    public static final String REGRA = "Use ao menos 6 caracteres e no máximo 72 bytes (acentos ocupam mais de um byte).";

    public static boolean valida(String senha) {
        return senha != null && !senha.isBlank() && senha.length() >= 6
                && senha.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
