package com.pfc.thindesk.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


//Token de uso único enviado por email.
@Document(collection = "email_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;

    private String username;

    private TipoToken tipo;

    /** Índice TTL: o MongoDB apaga o documento sozinho depois desta data. */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiraEm;
}
