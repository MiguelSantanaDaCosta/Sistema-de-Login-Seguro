package com.pfc.thindesk.util;

import java.text.Normalizer;

/**
 * Regra única do nome de usuário (cadastro e login).
 *
 * Aceita letras LATINAS com acento (joão, José), números, espaço simples entre palavras e . _ - @ +.
 * Letras de outros alfabetos (cirílico, grego…) são recusadas de propósito: elas têm formas idênticas às
 * latinas e permitiriam criar um nome visualmente igual ao de outra pessoa (ex.: "santаna" com "а" cirílico).
 * Também ficam de fora / \ ? # % : ; e aspas, que quebram URLs e HTML.
 */
public final class NomeUsuario {

    private NomeUsuario() {
    }

    /** 3 a 30 caracteres; espaço só entre palavras (sem espaços nas pontas nem duplos). */
    public static final String REGEX =
            "^(?=.{3,30}$)[\\p{IsLatin}\\p{M}\\p{N}._@+-]+( [\\p{IsLatin}\\p{M}\\p{N}._@+-]+)*$";

    public static final String DESCRICAO =
            "3 a 30 caracteres: letras (com acento), números, espaço entre palavras e . _ - @ +";

    /** Remove espaços das pontas e unifica a acentuação (NFC), para "é" digitado de qualquer forma ser o mesmo nome. */
    public static String normalizar(String nome) {
        return nome == null ? null : Normalizer.normalize(nome.trim(), Normalizer.Form.NFC);
    }
}
