package com.pfc.thindesk.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Entidade do puzzle de xadrez (id do Lichess, FEN, lance correto, rating).
@Document(collection = "puzzles_xadrez")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PuzzleXadrez {
    @Id
    private String id;
    private String fen;
    private String lanceCorreto;
    private String dificuldade;
    private String descricao;

    /**
     * Rating do Lichess; usado para limitar a dificuldade dos puzzles sorteados.
     */
    private Integer rating;
}
