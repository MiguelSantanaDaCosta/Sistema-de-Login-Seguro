package com.pfc.thindesk.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.pfc.thindesk.entity.PuzzleXadrez;

// Acesso aos puzzles do Lichess importados.
public interface PuzzleXadrezRepository extends MongoRepository<PuzzleXadrez, String> {

    /**
     * Sorteia 1 puzzle com rating <= ratingMax, diferente de excluirId
     */
    @Aggregation(pipeline = {
            "{ $match: { rating: { $lte: ?0 }, _id: { $ne: ?1 } } }",
            "{ $sample: { size: 1 } }"
    })
    // Sorteia 1 puzzle com rating <= ratingMax, diferente de excluirId.
    List<PuzzleXadrez> sortearAteRating(int ratingMax, String excluirId);

    /** Sorteia 1 puzzle com min <= rating <= max, diferente de excluirId (faixa por perfil). */
    @Aggregation(pipeline = {
            "{ $match: { rating: { $gte: ?0, $lte: ?1 }, _id: { $ne: ?2 } } }",
            "{ $sample: { size: 1 } }"
    })
    List<PuzzleXadrez> sortearEntre(int min, int max, String excluirId);

    /** Os 50 puzzles de maior rating do banco (usado pelo desafio root). */
    List<PuzzleXadrez> findTop50ByOrderByRatingDesc();

    /** Puzzles com rating >= min (elite do desafio root). */
    List<PuzzleXadrez> findByRatingGreaterThanEqual(int min);

    long countByRatingGreaterThanEqual(int min);

    /** Puzzles com min <= rating <= max (faixas dos desafios de promoção). */
    List<PuzzleXadrez> findByRatingBetween(int min, int max);

    long countByRatingBetween(int min, int max);
}
