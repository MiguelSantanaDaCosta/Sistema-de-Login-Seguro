package com.pfc;

import com.pfc.thindesk.entity.PuzzleXadrez;
import com.pfc.thindesk.repository.PuzzleXadrezRepository;
import com.pfc.thindesk.util.FenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


  //Importa puzzles do banco público do Lichess (licença CC0) para a coleção puzzles_xadrez.

// Importa puzzles do Lichess (CSV) para a coleção puzzles_xadrez no boot.
@Component
public class PuzzleImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PuzzleImporter.class);

    // base geral (todas as faixas) + elite (rating >= 3000, só para o desafio root)
    private static final List<String> ARQUIVOS_CSV = List.of("puzzles_lichess.csv", "puzzles_lichess_elite.csv");
    private static final int TAMANHO_LOTE = 500;

    @Autowired
    private PuzzleXadrezRepository puzzleXadrezRepository;
    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    // Teto do import: precisa cobrir as faixas de todos os perfis e o desafio root
    @Value("${app.security.puzzle.import-rating-max:4000}")
    private int ratingMax;

    // Rating mínimo dos puzzles do desafio root: o import precisa trazer ao menos alguns
    @Value("${app.root.rating-min:3000}")
    private int ratingMinRoot;

    // Importa os CSVs em lotes. Idempotente: só insere o que ainda não existe (por _id).
    @Override
    public void run(String... args) {
        List<PuzzleXadrez> maisAltos = puzzleXadrezRepository.findTop50ByOrderByRatingDesc();
        boolean temDificeis = !maisAltos.isEmpty() && maisAltos.get(0).getRating() != null
                && maisAltos.get(0).getRating() >= 2000;
        boolean temElite = puzzleXadrezRepository.countByRatingGreaterThanEqual(ratingMinRoot) >= 20;
        if (temDificeis && temElite) {
            log.info("PuzzleImporter: coleção puzzles_xadrez já populada — nada a importar.");
            return;
        }
        for (String arquivo : ARQUIVOS_CSV) {
            importar(arquivo);
        }
    }

    private void importar(String arquivo) {
        ClassPathResource recurso = new ClassPathResource(arquivo);
        if (!recurso.exists()) {
            log.warn("PuzzleImporter: {} não encontrado em src/main/resources. "
                    + "O login 2FA não funcionará sem puzzles — veja o README.", arquivo);
            return;
        }

        int lidas = 0;
        int importados = 0;
        List<PuzzleXadrez> lote = new ArrayList<>(TAMANHO_LOTE);

        try (BufferedReader leitor = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {

            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank() || linha.startsWith("PuzzleId")) {
                    continue; // pula cabeçalho e linhas vazias
                }
                lidas++;

                PuzzleXadrez puzzle = converter(linha);
                if (puzzle == null) {
                    continue; // descartado (ver regras em converter)
                }

                lote.add(puzzle);
                if (lote.size() >= TAMANHO_LOTE) {
                    gravar(lote);
                    importados += lote.size();
                    lote.clear();
                }
            }
            if (!lote.isEmpty()) {
                gravar(lote);
                importados += lote.size();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + arquivo, e);
        }

        log.info("PuzzleImporter: {} — {} puzzles válidos (rating <= {}), {} linhas lidas, {} descartadas.",
                arquivo, importados, ratingMax, lidas, lidas - importados);
    }

    
     //Converte uma linha do CSV em PuzzleXadrez, ou devolve null se o puzzle não servir
     
    // Converte uma linha do CSV em PuzzleXadrez (filtra por rating).
    private PuzzleXadrez converter(String linha) {
        String[] c = linha.split(",", -1);
        if (c.length < 8) {
            return null;
        }

        int rating;
        try {
            rating = Integer.parseInt(c[3].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (rating > ratingMax) {
            return null;
        }

        String[] lances = c[2].trim().split("\\s+");
        if (lances.length < 4) {
            return null;
        }
        String lanceCorreto = lances[1];
        if (lanceCorreto.length() != 4) {
            return null;
        }

        // Posição que o usuário realmente vê: FEN original + lance do adversário
        String fenDoPuzzle;
        try {
            fenDoPuzzle = FenUtil.aplicarLance(c[1].trim(), lances[0]);
        } catch (RuntimeException e) {
            return null;
        }

        String vez = "b".equals(fenDoPuzzle.split(" ")[1]) ? "Pretas" : "Brancas";
        String temas = c[7].trim();

        PuzzleXadrez puzzle = new PuzzleXadrez();
        puzzle.setId(c[0].trim()); // PuzzleId do Lichess como _id => sem duplicatas
        puzzle.setFen(fenDoPuzzle);
        puzzle.setLanceCorreto(lanceCorreto);
        puzzle.setRating(rating);
        puzzle.setDificuldade(dificuldadePorRating(rating));
        puzzle.setDescricao(vez + " jogam · rating " + rating + (temas.isEmpty() ? "" : " · " + temas));
        return puzzle;
    }

    /** < 1400 -> facil | < 1900 -> media | resto -> dificil */
    private String dificuldadePorRating(int rating) {
        if (rating < 1400) return "facil";
        if (rating < 1900) return "media";
        return "dificil";
    }

    // Insere em lote só os que ainda não existem (uma ida ao banco por lote, não por documento)
    private void gravar(List<PuzzleXadrez> lote) {
        java.util.Set<String> existentes = new java.util.HashSet<>();
        puzzleXadrezRepository.findAllById(lote.stream().map(PuzzleXadrez::getId).toList())
                .forEach(p -> existentes.add(p.getId()));
        List<PuzzleXadrez> novos = lote.stream().filter(p -> !existentes.contains(p.getId())).toList();
        if (!novos.isEmpty()) {
            mongoTemplate.insert(novos, PuzzleXadrez.class);
        }
    }
}
