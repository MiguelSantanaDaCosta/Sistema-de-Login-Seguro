package com.pfc.thindesk.root;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pfc.thindesk.entity.PuzzleXadrez;
import com.pfc.thindesk.entity.RootDesafio;
import com.pfc.thindesk.repository.PuzzleXadrezRepository;
import com.pfc.thindesk.repository.RootDesafioRepository;

/**
 * Desafios de xadrez (rainha, cavalo e torre): N puzzles SEGUIDOS (sem errar) dentro do tempo total,
 * sorteados numa faixa de rating por tipo. Todo o estado fica no servidor.
 */
@Service
public class RootDesafioService {

    /** Resultado de uma operação: status HTTP sugerido + corpo JSON. */
    public record Resposta(int http, Map<String, Object> corpo, boolean concluido, String desafioId, boolean bot) {
        public Resposta(int http, Map<String, Object> corpo, boolean concluido, String desafioId) {
            this(http, corpo, concluido, desafioId, false);
        }
    }

    @Autowired
    private RootDesafioRepository repo;
    @Autowired
    private PuzzleXadrezRepository puzzles;
    @Autowired
    private RootProps props;

    /** Começa uma tentativa. usuario = dono nos desafios de promoção; null na rainha (anônimo). */
    public Resposta iniciar(TipoDesafio tipo, String usuario) {
        List<PuzzleXadrez> pool = pool(tipo);
        if (pool.size() < props.puzzles(tipo)) {
            return new Resposta(500, mapa("erro", "Puzzles insuficientes no banco para o desafio."), false, null);
        }
        RootDesafio d = new RootDesafio();
        d.setId(UUID.randomUUID().toString());
        d.setTipo(tipo.name());
        d.setUsuario(usuario);
        d.setExpiraEm(System.currentTimeMillis() + props.tempo(tipo) * 1000L);
        PuzzleXadrez primeiro = sortear(d, pool);
        repo.save(d);
        return new Resposta(200, dados(tipo, d, primeiro), false, d.getId());
    }

    public Resposta responder(TipoDesafio tipo, String usuario, String desafioId, String lance) {
        Optional<RootDesafio> opt = desafioId == null ? Optional.empty() : repo.findById(desafioId);
        // o id só vale para o mesmo tipo de desafio e para o mesmo usuário que o iniciou
        if (opt.isEmpty() || !tipo.name().equals(opt.get().getTipo()) || !Objects.equals(usuario, opt.get().getUsuario())) {
            return new Resposta(409, mapa("erro", "Nenhum desafio ativo. Comece novamente.", "reiniciar", true),
                    false, null);
        }
        RootDesafio d = opt.get();

        if (System.currentTimeMillis() > d.getExpiraEm()) {
            repo.delete(d);
            return new Resposta(410, mapa("erro", "O tempo acabou.", "reiniciar", true), false, null);
        }

        // Humano nenhum resolve puzzle de rating alto em poucos segundos: resposta antes do mínimo = bot/engine
        long minMs = props.getMinSegundosPorPuzzle() * 1000L;
        if (minMs > 0 && System.currentTimeMillis() - d.getPuzzleEmitidoEm() < minMs) {
            repo.delete(d);
            return new Resposta(418, mapa("erro", "Rápido demais."), false, null, true);
        }

        PuzzleXadrez atual = puzzles.findById(d.getPuzzleAtualId()).orElse(null);
        String esperado = atual == null || atual.getLanceCorreto() == null ? "" : atual.getLanceCorreto().trim().toLowerCase();
        String enviado = lance == null ? "" : lance.trim().toLowerCase();

        if (esperado.isEmpty() || !esperado.equals(enviado)) {
            repo.delete(d); // errou: "seguidos" => tudo recomeça
            return new Resposta(403, mapa("erro", "Lance errado. O desafio foi reiniciado.", "reiniciar", true),
                    false, null);
        }

        d.setResolvidos(d.getResolvidos() + 1);
        if (d.getResolvidos() >= props.puzzles(tipo)) {
            repo.delete(d);
            return new Resposta(200, mapa("concluido", true), true, null);
        }

        PuzzleXadrez proximo = sortear(d, pool(tipo));
        repo.save(d);
        return new Resposta(200, dados(tipo, d, proximo), false, d.getId());
    }

    // Puzzles do desafio. Rainha: rating >= mínimo (se o banco tiver poucos, cai para os 50 mais altos).
    // Cavalo e torre: faixa [mínimo, máximo].
    private List<PuzzleXadrez> pool(TipoDesafio tipo) {
        Integer max = props.max(tipo);
        if (max != null) {
            return puzzles.findByRatingBetween(props.min(tipo), max);
        }
        List<PuzzleXadrez> lista = puzzles.findByRatingGreaterThanEqual(props.min(tipo));
        return lista.size() >= props.puzzles(tipo) ? lista : puzzles.findTop50ByOrderByRatingDesc();
    }

    // Sorteia um puzzle ainda não usado nesta tentativa
    private PuzzleXadrez sortear(RootDesafio d, List<PuzzleXadrez> pool) {
        List<PuzzleXadrez> livres = pool.stream().filter(p -> !d.getUsados().contains(p.getId())).toList();
        PuzzleXadrez p = livres.get(ThreadLocalRandom.current().nextInt(livres.size()));
        d.getUsados().add(p.getId());
        d.setPuzzleAtualId(p.getId());
        d.setPuzzleEmitidoEm(System.currentTimeMillis());
        return p;
    }

    private Map<String, Object> dados(TipoDesafio tipo, RootDesafio d, PuzzleXadrez p) {
        long restanteMs = Math.max(0, d.getExpiraEm() - System.currentTimeMillis());
        return mapa(
                "fen", p.getFen(),
                "vez", "b".equals(p.getFen().split(" ")[1]) ? "pretas" : "brancas",
                "rating", p.getRating(),
                "puzzleNumero", d.getResolvidos() + 1,
                "puzzlesTotal", props.puzzles(tipo),
                "expiraEmSegundos", (restanteMs + 999) / 1000,
                "tempoTotalSegundos", props.tempo(tipo));
    }

    private static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], pares[i + 1]);
        }
        return m;
    }
}
