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

    @Value("${app.security.puzzle.rating-max:1100}")
    private int ratingMax;
    @Value("${app.security.puzzle.tempo-limite-segundos:300}")
    private int tempoLimiteSegundos;
    @Value("${app.security.puzzle.max-puzzles-por-sessao:3}")
    private int maxPuzzlesPorSessao;
    @Value("${app.security.puzzle.max-tentativas:5}")
    private int maxTentativas;

    // Início da sessão: zera contador e sorteia o primeiro
    public PuzzleXadrez iniciarSessao(Usuario usuario) {
        usuario.setPuzzlesNaSessao(0);
        return emitir(usuario);
    }

    // Troca de puzzle, respeitando o teto
    public PuzzleXadrez renovar(Usuario usuario) {
        if (usuario.getPuzzlesNaSessao() >= maxPuzzlesPorSessao) {
            limpar(usuario);
            throw new LimiteAtingidoException();
        }
        return emitir(usuario);
    }

    // Puzzle ativo, se houver
    public Optional<PuzzleXadrez> atual(Usuario usuario) {
        if (usuario.getPuzzleAtualId() == null) {
            return Optional.empty();
        }
        return puzzleRepository.findById(usuario.getPuzzleAtualId());
    }

    // Verifica prazo
    public boolean expirou(Usuario usuario) {
        return usuario.getPuzzleExpiraEm() == null
                || System.currentTimeMillis() > usuario.getPuzzleExpiraEm();
    }

    // Encerra o ciclo do puzzle
    public void limpar(Usuario usuario) {
        usuario.setPuzzleAtualId(null);
        usuario.setPuzzleExpiraEm(null);
        usuario.setTentativasPuzzle(0);
        usuario.setPuzzlesNaSessao(0);
        usuarioRepository.save(usuario);
    }

    // JSON enviado ao front (sem o lanceCorreto)
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

    // Sorteia e ativa um puzzle no usuário
    private PuzzleXadrez emitir(Usuario usuario) {
        // Não sorteia o puzzle atual de novo
        String excluir = usuario.getPuzzleAtualId() == null ? "" : usuario.getPuzzleAtualId();

        List<PuzzleXadrez> sorteado = puzzleRepository.sortearAteRating(ratingMax, excluir);
        if (sorteado.isEmpty()) {
            // Talvez o único elegível seja o atual
            sorteado = puzzleRepository.sortearAteRating(ratingMax, "");
        }
        if (sorteado.isEmpty()) {
            throw new SemPuzzlesException("Nenhum puzzle com rating até " + ratingMax
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
}
