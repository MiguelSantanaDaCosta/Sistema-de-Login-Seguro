package com.pfc.thindesk.security.bot;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IpBloqueadoRepository extends MongoRepository<IpBloqueado, String> {
}
