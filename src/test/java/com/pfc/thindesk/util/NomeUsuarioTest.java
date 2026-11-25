package com.pfc.thindesk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class NomeUsuarioTest {

    private static final Pattern P = Pattern.compile(NomeUsuario.REGEX);

    private static boolean ok(String nome) {
        return P.matcher(NomeUsuario.normalizar(nome)).matches();
    }

    @Test
    void aceitaLetrasNumerosAcentosEPontuacaoPermitida() {
        for (String n : new String[] { "ana", "Ana99", "ana.silva", "ana_silva", "ana-silva", "joão", "José da Silva",
                "maria.josé", "ÀÉÎÕÜ ção", "a_b-c@d+e", "abc", "a".repeat(30) }) {
            assertTrue(ok(n), "deveria aceitar: " + n);
        }
    }

    @Test
    void recusaTamanhoEEspacosInvalidos() {
        assertFalse(P.matcher("ab").matches(), "curto demais");
        assertFalse(P.matcher("a".repeat(31)).matches(), "longo demais");
        assertFalse(P.matcher(" ana").matches(), "espaço no início");
        assertFalse(P.matcher("ana ").matches(), "espaço no fim");
        assertFalse(P.matcher("ana  silva").matches(), "espaço duplo");
        assertFalse(P.matcher("   ").matches(), "só espaços");
    }

    @Test
    void recusaCaracteresQueQuebramUrlOuHtml() {
        for (String n : new String[] { "ana/silva", "ana\\silva", "ana?x", "ana#1", "ana%20", "a:b:c", "a;b;c", "ana<b>",
                "ana\"x", "ana'x", "ana!", "ana$", "ana&b", "ana(1)" }) {
            assertFalse(P.matcher(n).matches(), "deveria recusar: " + n);
        }
    }

    @Test
    void recusaLetrasDeOutrosAlfabetosQueImitamAsLatinas() {
        String santanaCirilico = "santаna"; // "а" cirílico no lugar do "a" latino
        assertFalse(P.matcher(santanaCirilico).matches(), "cirílico deve ser recusado");
        assertFalse(P.matcher("αna").matches(), "grego deve ser recusado");
        assertFalse(P.matcher("山田").matches(), "ideogramas devem ser recusados");
    }

    @Test
    void normalizaAcentuacaoParaAMesmaFormaEAparaEspacos() {
        String decomposto = "joão"; // "a" + til combinante (NFD)
        assertEquals("joão", NomeUsuario.normalizar(decomposto));
        assertEquals("ana", NomeUsuario.normalizar("  ana  "));
        assertTrue(ok(decomposto), "forma decomposta também é aceita (e depois normalizada)");
    }
}
