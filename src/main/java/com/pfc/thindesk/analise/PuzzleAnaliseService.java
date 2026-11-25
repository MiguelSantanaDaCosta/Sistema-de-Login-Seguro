package com.pfc.thindesk.analise;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.thindesk.entity.PuzzleAnalise;
import com.pfc.thindesk.entity.PuzzleAnalise.Jogo;
import com.pfc.thindesk.entity.PuzzleXadrez;
import com.pfc.thindesk.repository.PuzzleAnaliseRepository;
import com.pfc.thindesk.repository.PuzzleXadrezRepository;

/**
 * Cruza nossos puzzles com dados PÚBLICOS do Lichess (sem token, sem dados de usuários nossos):
 *  - banco de Mestres (explorer.lichess.org/masters): a posição já apareceu em partida oficial? quais jogadores/ano?
 *  - exportação da partida de origem do puzzle (GameUrl do CSV): jogadores, títulos, ano.
 * Roda em segundo plano, uma amostra fixa e retomável, respeitando o limite de ~1 requisição/segundo.
 */
@Service
public class PuzzleAnaliseService {

    private static final Logger log = LoggerFactory.getLogger(PuzzleAnaliseService.class);
    private static final Pattern GAME_ID = Pattern.compile("lichess\\.org/([A-Za-z0-9]{8})");
    private static final Pattern TAG = Pattern.compile("\\[(\\w+) \"([^\"]*)\"\\]");
    private static final Set<String> TITULOS_GM = Set.of("GM", "WGM");

    public record Destaque(String categoria, int rating, String jogadores, Integer ano, String origem, String link) {
    }

    public record Resumo(long puzzlesNaBase, long analisados, long mestres, long gm, long titulados,
            long comuns, List<Destaque> destaques, boolean emAndamento) {
    }

    @Autowired
    private PuzzleXadrezRepository puzzles;
    @Autowired
    private PuzzleAnaliseRepository repo;

    @Value("${app.analise.enabled:false}")
    private boolean habilitada;
    @Value("${app.analise.amostra:1500}")
    private int amostra;
    @Value("${app.analise.intervalo-ms:1100}")
    private long intervaloMs;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private volatile boolean rodando;

    // cache do resumo (o dashboard é acessado com frequência)
    private volatile Resumo cache;
    private volatile long cacheEm;

    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        if (!habilitada) {
            return;
        }
        Thread t = new Thread(this::executar, "puzzle-analise");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ job

    private void executar() {
        rodando = true;
        try {
            Map<String, String> gameUrls = carregarGameUrls();
            Set<String> feitos = new HashSet<>();
            repo.findAll().forEach(a -> feitos.add(a.getId()));

            List<PuzzleXadrez> candidatos = new ArrayList<>();
            for (PuzzleXadrez p : puzzles.findAll()) {
                if (!feitos.contains(p.getId()) && gameUrls.containsKey(p.getId())) {
                    candidatos.add(p);
                }
            }
            Collections.shuffle(candidatos, new Random(42)); // amostra fixa e retomável
            int faltam = Math.max(0, amostra - feitos.size());
            log.info("Análise Lichess: {} já feitos, {} a analisar nesta rodada (amostra {}).", feitos.size(),
                    Math.min(faltam, candidatos.size()), amostra);

            int n = 0;
            for (PuzzleXadrez p : candidatos) {
                if (n >= faltam) {
                    break;
                }
                try {
                    repo.save(analisar(p, gameUrls.get(p.getId())));
                    n++;
                    if (n % 100 == 0) {
                        log.info("Análise Lichess: {} puzzles analisados nesta rodada.", n);
                    }
                } catch (RuntimeException e) {
                    log.warn("Análise Lichess: falha no puzzle {}: {}", p.getId(), e.getMessage());
                }
            }
            log.info("Análise Lichess concluída: {} novos puzzles analisados.", n);
        } catch (Exception e) {
            log.error("Análise Lichess interrompida: {}", e.toString());
        } finally {
            rodando = false;
        }
    }

    private PuzzleAnalise analisar(PuzzleXadrez p, String gameUrl) {
        PuzzleAnalise a = new PuzzleAnalise();
        a.setId(p.getId());
        a.setRating(p.getRating());

        // 1) banco de Mestres: a posição que o usuário vê já ocorreu em partida oficial?
        String url = "https://explorer.lichess.org/masters?moves=0&topGames=4&fen="
                + URLEncoder.encode(p.getFen(), StandardCharsets.UTF_8);
        String corpo = get(url, "application/json");
        pausa();
        if (corpo != null) {
            try {
                JsonNode d = json.readTree(corpo);
                a.setPartidasMestres(d.path("white").asInt() + d.path("draws").asInt() + d.path("black").asInt());
                for (JsonNode g : d.path("topGames")) {
                    Jogo j = new Jogo();
                    j.setId(g.path("id").asText());
                    j.setLink("https://lichess.org/" + j.getId());
                    j.setBrancas(g.path("white").path("name").asText(null));
                    j.setPretas(g.path("black").path("name").asText(null));
                    j.setEloBrancas(g.path("white").has("rating") ? g.path("white").path("rating").asInt() : null);
                    j.setEloPretas(g.path("black").has("rating") ? g.path("black").path("rating").asInt() : null);
                    j.setAno(g.has("year") ? g.path("year").asInt() : null);
                    a.getJogosMestres().add(j);
                }
            } catch (Exception e) {
                log.debug("JSON inesperado do explorer para {}", p.getId());
            }
        }

        // 2) a partida real de onde o puzzle foi extraído
        Matcher m = GAME_ID.matcher(gameUrl);
        if (m.find()) {
            String id = m.group(1);
            String pgn = get("https://lichess.org/game/export/" + id
                    + "?moves=false&clocks=false&evals=false&tags=true", "application/x-chess-pgn");
            pausa();
            if (pgn != null) {
                Map<String, String> t = new HashMap<>();
                Matcher mt = TAG.matcher(pgn);
                while (mt.find()) {
                    t.put(mt.group(1), mt.group(2));
                }
                Jogo o = new Jogo();
                o.setId(id);
                o.setLink("https://lichess.org/" + id);
                o.setBrancas(t.get("White"));
                o.setPretas(t.get("Black"));
                o.setEloBrancas(inteiro(t.get("WhiteElo")));
                o.setEloPretas(inteiro(t.get("BlackElo")));
                o.setTituloBrancas(t.get("WhiteTitle"));
                o.setTituloPretas(t.get("BlackTitle"));
                String data = t.get("UTCDate") != null ? t.get("UTCDate") : t.get("Date");
                o.setAno(data != null && data.length() >= 4 ? inteiro(data.substring(0, 4)) : null);
                a.setOrigem(o);
            }
        }

        a.setCategoria(classificar(a));
        return a;
    }

    // MESTRES > GM > TITULADO > COMUM
    private String classificar(PuzzleAnalise a) {
        if (a.getPartidasMestres() > 0) {
            return "MESTRES";
        }
        Jogo o = a.getOrigem();
        if (o == null) {
            return "COMUM";
        }
        if (ehGm(o.getTituloBrancas()) || ehGm(o.getTituloPretas())) {
            return "GM";
        }
        if (o.getTituloBrancas() != null || o.getTituloPretas() != null) {
            return "TITULADO";
        }
        return "COMUM";
    }

    // Set.of(...).contains(null) lança NPE; jogador sem título é o caso comum
    private boolean ehGm(String titulo) {
        return titulo != null && TITULOS_GM.contains(titulo);
    }

    // ------------------------------------------------------------------ HTTP

    private String get(String url, String accept) {
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "thindesk-study/1.0").header("Accept", accept).GET().build();
                HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 429) {
                    log.warn("Lichess respondeu 429; aguardando 65s.");
                    Thread.sleep(65_000);
                    continue;
                }
                return r.statusCode() == 200 ? r.body() : null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void pausa() {
        try {
            Thread.sleep(intervaloMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Integer inteiro(String s) {
        try {
            return s == null ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // PuzzleId -> GameUrl, lido dos mesmos CSVs do importador
    private Map<String, String> carregarGameUrls() throws Exception {
        Map<String, String> m = new HashMap<>();
        for (String arq : List.of("puzzles_lichess.csv", "puzzles_lichess_elite.csv")) {
            ClassPathResource r = new ClassPathResource(arq);
            if (!r.exists()) {
                continue;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = br.readLine()) != null) {
                    if (linha.startsWith("PuzzleId") || linha.isBlank()) {
                        continue;
                    }
                    String[] c = linha.split(",", -1);
                    if (c.length > 8 && !c[8].isBlank()) {
                        m.put(c[0].trim(), c[8].trim());
                    }
                }
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ resumo p/ dashboard

    public Resumo resumo() {
        long agora = System.currentTimeMillis();
        Resumo c = cache;
        if (c != null && agora - cacheEm < 60_000) {
            return c;
        }
        long total = repo.count();
        long mestres = repo.countByCategoria("MESTRES");
        long gm = repo.countByCategoria("GM");
        long titulados = repo.countByCategoria("TITULADO");

        List<Destaque> d = new ArrayList<>();
        for (String cat : List.of("MESTRES", "GM", "TITULADO")) {
            for (PuzzleAnalise a : repo.findByCategoriaOrderByRatingDesc(cat, PageRequest.of(0, 4))) {
                d.add(destaque(a));
            }
        }
        Resumo r = new Resumo(puzzles.count(), total, mestres, gm, titulados,
                Math.max(0, total - mestres - gm - titulados), d.stream().limit(8).toList(), rodando);
        cache = r;
        cacheEm = agora;
        return r;
    }

    private Destaque destaque(PuzzleAnalise a) {
        boolean mestres = "MESTRES".equals(a.getCategoria()) && !a.getJogosMestres().isEmpty();
        Jogo j = mestres ? a.getJogosMestres().get(0) : a.getOrigem();
        String brancas = j.getBrancas() + (j.getTituloBrancas() != null ? " (" + j.getTituloBrancas() + ")" : "");
        String pretas = j.getPretas() + (j.getTituloPretas() != null ? " (" + j.getTituloPretas() + ")" : "");
        return new Destaque(a.getCategoria(), a.getRating() == null ? 0 : a.getRating(), brancas + " × " + pretas,
                j.getAno(), mestres ? "Partida de mestres (OTB)" : "Partida online do Lichess", j.getLink());
    }
}
