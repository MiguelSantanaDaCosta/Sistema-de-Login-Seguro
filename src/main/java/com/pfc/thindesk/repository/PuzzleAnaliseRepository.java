package com.pfc.thindesk.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.pfc.thindesk.entity.PuzzleAnalise;

public interface PuzzleAnaliseRepository extends MongoRepository<PuzzleAnalise, String> {

    long countByCategoria(String categoria);

    long countByPartidasMestresGreaterThan(int n);

    List<PuzzleAnalise> findByCategoriaOrderByRatingDesc(String categoria, Pageable pageable);
}
