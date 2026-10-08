package com.pfc.thindesk.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.pfc.thindesk.entity.PuzzleXadrez;

public interface PuzzleXadrezRepository extends MongoRepository<PuzzleXadrez, String> {

    /**
     * Sorteia 1 puzzle com rating <= ratingMax, diferente de excluirId
     */
    @Aggregation(pipeline = {
            "{ $match: { rating: { $lte: ?0 }, _id: { $ne: ?1 } } }",
            "{ $sample: { size: 1 } }"
    })
    List<PuzzleXadrez> sortearAteRating(int ratingMax, String excluirId);
}
