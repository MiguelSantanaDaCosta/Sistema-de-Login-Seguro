// ============================================================
// PuzzleService.java
// Autor: Miguel Santana
// Descrição: Regras do 2FA por puzzle. Estado fica no usuário.
//            Cliente não decide puzzle, prazo nem tentativas.
// ============================================================
package com.pfc.thindesk.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pfc.thindesk.entity.PuzzleXadrez;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.PuzzleXadrezRepository;
import com.pfc.thindesk.repository.UsuarioRepository;

// Serviço responsável pelas regras do 2FA via puzzle de xadrez.
@Service
public class PuzzleService {

    // Estourou o teto de puzzles da sessão
    public static class LimiteAtingidoException extends RuntimeException {
        public LimiteAtingidoException() {
            super("Limite de puzzles atingido.");
        }
    }

    // Nenhum puzzle elegível no banco
    public static class SemPuzzlesException extends RuntimeException {
        public SemPuzzlesException(String mensagem) {
            super(mensagem);
        }
    }

    @Autowired
    private PuzzleXadrezRepository puzzleRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    // Faixa de rating por perfil (o maior perfil do usuário define a faixa)
    @Value("${app.security.puzzle.faixa.usuario.min:1100}")
    private int usuarioMin;
    @Value("${app.security.puzzle.faixa.usuario.max:1399}")
    private int usuarioMax;
    // Conta do dono (identificada por email vindo de variável de ambiente): puzzles sempre fáceis
    @Value("${app.security.puzzle.email-facil:}")
    private String emailFacil;
    @Value("${app.security.puzzle.facil-max:1000}")
    private int facilMax;
    @Value("${app.security.puzzle.faixa.tecnico.min:1400}")
    private int tecnicoMin;
    @Value("${app.security.puzzle.faixa.tecnico.max:1800}")
    private int tecnicoMax;
    @Value("${app.security.puzzle.faixa.admin.min:1900}")
    private int adminMin;
    @Value("${app.security.puzzle.faixa.admin.max:2400}")
    private int adminMax;
    @Value("${app.security.puzzle.tempo-limite-segundos:300}")
    private int tempoLimiteSegundos;
    @Value("${app.security.puzzle.max-puzzles-por-sessao:3}")
    private int maxPuzzlesPorSessao;
    @Value("${app.security.puzzle.max-tentativas:5}")
    private int maxTentativas;

    // Início da sessão: zera contador e sorteia o primeiro
    // Inicia a sessão do usuário, resetando contadores e sorteando o primeiro puzzle.
    public PuzzleXadrez iniciarSessao(Usuario usuario) {
        usuario.setPuzzlesNaSessao(0);
        return emitir(usuario);
    }

    // Troca de puzzle, respeitando o teto
    // Gera um novo puzzle, respeitando o limite máximo por sessão.
    public PuzzleXadrez renovar(Usuario usuario) {
        if (usuario.getPuzzlesNaSessao() >= maxPuzzlesPorSessao) {
            limpar(usuario);
            throw new LimiteAtingidoException();
        }
        return emitir(usuario);
    }

    // Retorna o puzzle ativo do usuário, se houver.
    public Optional<PuzzleXadrez> atual(Usuario usuario) {
        if (usuario.getPuzzleAtualId() == null) {
            return Optional.empty();
        }
        return puzzleRepository.findById(usuario.getPuzzleAtualId());
    }

    // Verifica se o prazo do puzzle atual expirou.
    public boolean expirou(Usuario usuario) {
        return usuario.getPuzzleExpiraEm() == null
                || System.currentTimeMillis() > usuario.getPuzzleExpiraEm();
    }

    // Limpa o estado do puzzle (usado ao concluir ou falhar).
    public void limpar(Usuario usuario) {
        usuario.setPuzzleAtualId(null);
        usuario.setPuzzleExpiraEm(null);
        usuario.setTentativasPuzzle(0);
        usuario.setPuzzlesNaSessao(0);
        usuarioRepository.save(usuario);
    }

    // JSON enviado ao front (sem o lanceCorreto)
    // Monta o JSON do puzzle para o front (sem o lance correto).
    public Map<String, Object> dados(PuzzleXadrez puzzle, Usuario usuario) {
        long restanteMs = usuario.getPuzzleExpiraEm() == null
                ? 0
                : Math.max(0, usuario.getPuzzleExpiraEm() - System.currentTimeMillis());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("puzzleId", puzzle.getId());
        m.put("fen", puzzle.getFen());
        m.put("vez", "b".equals(puzzle.getFen().split(" ")[1]) ? "pretas" : "brancas");
        m.put("rating", puzzle.getRating());
        m.put("expiraEmSegundos", (restanteMs + 999) / 1000);
        m.put("tempoLimiteSegundos", tempoLimiteSegundos);
        m.put("maxTentativas", maxTentativas);
        m.put("restantes", maxTentativas - usuario.getTentativasPuzzle());
        m.put("puzzleNumero", usuario.getPuzzlesNaSessao());
        m.put("puzzlesMax", maxPuzzlesPorSessao);
        return m;
    }

    // Sorteia um puzzle no banco e o ativa para o usuário.
    private PuzzleXadrez emitir(Usuario usuario) {
        // Não sorteia o puzzle atual de novo
        String excluir = usuario.getPuzzleAtualId() == null ? "" : usuario.getPuzzleAtualId();

        int[] faixa = faixaDe(usuario);
        List<PuzzleXadrez> sorteado = puzzleRepository.sortearEntre(faixa[0], faixa[1], excluir);
        if (sorteado.isEmpty()) {
            // Talvez o único elegível seja o atual
            sorteado = puzzleRepository.sortearEntre(faixa[0], faixa[1], "");
        }
        if (sorteado.isEmpty()) {
            throw new SemPuzzlesException("Nenhum puzzle com rating entre " + faixa[0] + " e " + faixa[1]
                    + " no banco. Gere o CSV e reimporte (veja o README).");
        }

        PuzzleXadrez puzzle = sorteado.get(0);

        usuario.setPuzzleAtualId(puzzle.getId());
        usuario.setPuzzleExpiraEm(System.currentTimeMillis() + tempoLimiteSegundos * 1000L);
        usuario.setTentativasPuzzle(0);
        usuario.setPuzzlesNaSessao(usuario.getPuzzlesNaSessao() + 1);
        usuarioRepository.save(usuario);

        return puzzle;
    }

    // Faixa [min, max] de rating conforme o maior perfil: ADMIN > TECNICO > USUARIO
    int[] faixaDe(Usuario usuario) {
        // Única conta com puzzles fáceis (<= facilMax), qualquer que seja o perfil
        if (emailFacil != null && !emailFacil.isBlank() && usuario.getEmail() != null
                && emailFacil.trim().equalsIgnoreCase(usuario.getEmail().trim())) {
            return new int[] { 0, facilMax };
        }
        var roles = usuario.getRoles();
        if (roles.contains(com.pfc.thindesk.entity.Role.ROLE_ADMIN)) {
            return new int[] { adminMin, adminMax };
        }
        if (roles.contains(com.pfc.thindesk.entity.Role.ROLE_TECNICO)) {
            return new int[] { tecnicoMin, tecnicoMax };
        }
        return new int[] { usuarioMin, usuarioMax };
    }
}
