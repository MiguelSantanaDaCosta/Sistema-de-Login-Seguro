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

@Component
public class PuzzleImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PuzzleImporter.class);

    private static final String ARQUIVO_CSV = "puzzles_lichess.csv";
    private static final int TAMANHO_LOTE = 500;

    @Autowired
    private PuzzleXadrezRepository puzzleXadrezRepository;

    @Value("${app.security.puzzle.rating-max:1100}")
    private int ratingMax;

    @Override
    public void run(String... args) {
        if (puzzleXadrezRepository.count() > 0) {
            log.info("PuzzleImporter: coleção puzzles_xadrez já populada — nada a importar.");
            return;
        }

        ClassPathResource recurso = new ClassPathResource(ARQUIVO_CSV);
        if (!recurso.exists()) {
            log.warn("PuzzleImporter: {} não encontrado em src/main/resources. "
                    + "O login 2FA não funcionará sem puzzles — veja o README.", ARQUIVO_CSV);
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
                    puzzleXadrezRepository.saveAll(lote);
                    importados += lote.size();
                    lote.clear();
                }
            }
            if (!lote.isEmpty()) {
                puzzleXadrezRepository.saveAll(lote);
                importados += lote.size();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + ARQUIVO_CSV, e);
        }

        log.info("PuzzleImporter: {} puzzles importados com rating <= {} ({} linhas lidas, {} descartadas).",
                importados, ratingMax, lidas, lidas - importados);
    }

    
     //Converte uma linha do CSV em PuzzleXadrez, ou devolve null se o puzzle não servir
     
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
}
