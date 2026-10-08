package com.pfc.thindesk.repository;

import com.pfc.thindesk.entity.ThemeConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ThemeConfigRepository extends MongoRepository<ThemeConfig, String> {
    Optional<ThemeConfig> findByUsername(String username);
}
