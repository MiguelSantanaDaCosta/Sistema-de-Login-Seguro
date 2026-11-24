package com.pfc.thindesk.security.bot;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/** IP bloqueado por suspeita de bot. O Mongo apaga o registro sozinho quando "ate" passa (TTL). */
@Document(collection = "ip_bloqueados")
@Data
public class IpBloqueado {

    @Id
    private String ip;

    @Indexed(expireAfter = "0s")
    private Instant ate;

    private String motivo;
    private String userAgent;
    private Instant criadoEm = Instant.now();
}
