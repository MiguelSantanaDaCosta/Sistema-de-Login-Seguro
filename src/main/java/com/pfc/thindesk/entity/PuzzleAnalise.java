package com.pfc.thindesk.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * Resultado do cruzamento de um puzzle da nossa base com os dados públicos do Lichess:
 * (1) a partida real de onde o puzzle foi extraído e (2) se a posição existe em partidas de mestres (OTB).
 */
@Document(collection = "puzzle_analise")
@Data
public class PuzzleAnalise {

    /** Mesmo id do puzzle (PuzzleId do Lichess). */
    @Id
    private String id;

    private Integer rating;
    private Instant analisadoEm = Instant.now();

    /** MESTRES (posição em partida de mestres) > GM > TITULADO (IM/FM/...) > COMUM. */
    @Indexed
    private String categoria = "COMUM";

    /** Quantas partidas do banco de Mestres do Lichess contêm a posição do puzzle. */
    private int partidasMestres;
    private List<Jogo> jogosMestres = new ArrayList<>();

    /** Partida real (online) de onde o puzzle foi extraído. */
    private Jogo origem;

    @Data
    public static class Jogo {
        private String id;
        private String link;
        private String brancas;
        private String pretas;
        private Integer eloBrancas;
        private Integer eloPretas;
        private String tituloBrancas;
        private String tituloPretas;
        private Integer ano;
    }
}
