package com.pfc.thindesk.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.pfc.thindesk.entity.EmailToken;
import com.pfc.thindesk.entity.TipoToken;

public interface EmailTokenRepository extends MongoRepository<EmailToken, String> {

    void deleteByUsernameAndTipo(String username, TipoToken tipo);

    boolean existsByUsernameAndTipoAndExpiraEmAfter(String username, TipoToken tipo, Instant agora);
}
