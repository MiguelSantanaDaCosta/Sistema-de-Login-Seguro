package com.pfc.thindesk.util;

/**
 * Utilitário mínimo para aplicar um lance (notação UCI, ex.: "e2e4") a uma
 * posição FEN.
 *
 * O puzzle de verdade começa DEPOIS desse lance. Como o projeto
 * não depende de uma engine de xadrez, aplicamos esse único lance "na mão".
 * Não valida a legalidade do lance (os dados do Lichess já são corretos);
 * apenas
 * atualiza peças, roque, en passant e contadores.
 */
public final class FenUtil {

    private FenUtil() {
    }

    /** Aplica o lance UCI ao FEN e devolve o novo FEN completo. */
    public static String aplicarLance(String fen, String lance) {
        if (fen == null || lance == null || lance.length() < 4) {
            throw new IllegalArgumentException("FEN ou lance inválido");
        }
        String[] partes = fen.trim().split("\\s+");
        if (partes.length < 4) {
            throw new IllegalArgumentException("FEN incompleto: " + fen);
        }

        char[][] tab = lerTabuleiro(partes[0]);
        boolean brancasJogam = partes[1].equals("w");
        String roque = partes[2];
        int meioLance = partes.length > 4 ? Integer.parseInt(partes[4]) : 0;
        int lanceCompleto = partes.length > 5 ? Integer.parseInt(partes[5]) : 1;

        // Converte "e2e4" em índices da matriz (linha 0 = rank 8; coluna 0 = file a)
        int colOrigem = lance.charAt(0) - 'a';
        int linOrigem = 8 - (lance.charAt(1) - '0');
        int colDestino = lance.charAt(2) - 'a';
        int linDestino = 8 - (lance.charAt(3) - '0');
        if (fora(colOrigem) || fora(linOrigem) || fora(colDestino) || fora(linDestino)) {
            throw new IllegalArgumentException("Lance fora do tabuleiro: " + lance);
        }

        char peca = tab[linOrigem][colOrigem];
        if (peca == ' ') {
            throw new IllegalArgumentException("Casa de origem vazia: " + lance);
        }
        boolean ehPeao = Character.toLowerCase(peca) == 'p';
        boolean ehRei = Character.toLowerCase(peca) == 'k';
        boolean captura = tab[linDestino][colDestino] != ' ';

        // En passant: peão anda na diagonal para casa vazia -> remove o peão capturado
        if (ehPeao && colOrigem != colDestino && !captura) {
            tab[linOrigem][colDestino] = ' ';
            captura = true;
        }

        // Roque: rei anda duas casas -> move também a torre
        if (ehRei && Math.abs(colDestino - colOrigem) == 2) {
            if (colDestino > colOrigem) { // roque curto
                tab[linOrigem][5] = tab[linOrigem][7];
                tab[linOrigem][7] = ' ';
            } else { // roque longo
                tab[linOrigem][3] = tab[linOrigem][0];
                tab[linOrigem][0] = ' ';
            }
        }

        // Move a peça (e promove, se o lance tiver 5 caracteres, ex.: "e7e8q")
        tab[linOrigem][colOrigem] = ' ';
        tab[linDestino][colDestino] = peca;
        if (lance.length() >= 5) {
            char promo = lance.charAt(4);
            tab[linDestino][colDestino] = Character.isUpperCase(peca)
                    ? Character.toUpperCase(promo)
                    : Character.toLowerCase(promo);
        }

        // Direitos de roque
        String novoRoque = roque;
        if (ehRei) {
            novoRoque = removerDireitos(novoRoque, Character.isUpperCase(peca) ? "KQ" : "kq");
        }
        novoRoque = removerPorCasa(novoRoque, linOrigem, colOrigem);
        novoRoque = removerPorCasa(novoRoque, linDestino, colDestino);
        if (novoRoque.isEmpty()) {
            novoRoque = "-";
        }

        // Casa de en passant (só quando um peão avança duas casas)
        String enPassant = "-";
        if (ehPeao && Math.abs(linDestino - linOrigem) == 2) {
            enPassant = "" + (char) ('a' + colOrigem) + (8 - (linOrigem + linDestino) / 2);
        }

        int novoMeioLance = (ehPeao || captura) ? 0 : meioLance + 1;
        int novoLanceCompleto = brancasJogam ? lanceCompleto : lanceCompleto + 1;

        return escreverTabuleiro(tab) + " " + (brancasJogam ? "b" : "w") + " " + novoRoque
                + " " + enPassant + " " + novoMeioLance + " " + novoLanceCompleto;
    }

    private static boolean fora(int i) {
        return i < 0 || i > 7;
    }

    private static String removerDireitos(String roque, String letras) {
        return roque.replaceAll("[" + letras + "]", "");
    }

    /**
     * Se um lance sai de / chega em um canto, o roque daquele lado deixa de
     * existir.
     */
    private static String removerPorCasa(String roque, int lin, int col) {
        if (lin == 7 && col == 0)
            return removerDireitos(roque, "Q");
        if (lin == 7 && col == 7)
            return removerDireitos(roque, "K");
        if (lin == 0 && col == 0)
            return removerDireitos(roque, "q");
        if (lin == 0 && col == 7)
            return removerDireitos(roque, "k");
        return roque;
    }

    private static char[][] lerTabuleiro(String placement) {
        String[] linhas = placement.split("/");
        if (linhas.length != 8) {
            throw new IllegalArgumentException("FEN inválido: " + placement);
        }
        char[][] tab = new char[8][8];
        for (int l = 0; l < 8; l++) {
            int c = 0;
            for (char ch : linhas[l].toCharArray()) {
                if (Character.isDigit(ch)) {
                    for (int k = 0; k < ch - '0'; k++) {
                        tab[l][c++] = ' ';
                    }
                } else {
                    tab[l][c++] = ch;
                }
            }
        }
        return tab;
    }

    private static String escreverTabuleiro(char[][] tab) {
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < 8; l++) {
            int vazias = 0;
            for (int c = 0; c < 8; c++) {
                if (tab[l][c] == ' ') {
                    vazias++;
                } else {
                    if (vazias > 0) {
                        sb.append(vazias);
                        vazias = 0;
                    }
                    sb.append(tab[l][c]);
                }
            }
            if (vazias > 0) {
                sb.append(vazias);
            }
            if (l < 7) {
                sb.append('/');
            }
        }
        return sb.toString();
    }
}
