package com.pfc.thindesk.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.pfc.thindesk.entity.RootDesafio;

public interface RootDesafioRepository extends MongoRepository<RootDesafio, String> {
}
