package com.pfc.thindesk.repositoty;

import com.pfc.thindesk.entity.PuzzleXadrez;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PuzzleXadrezRepository extends MongoRepository<PuzzleXadrez, String> {

}

