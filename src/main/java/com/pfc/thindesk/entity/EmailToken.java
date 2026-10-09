package com.pfc.thindesk.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "email_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailToken {

    // Id interno do documento no MongoDB.
    @Id
    private String id;

    // SHA-256 do token (o valor bruto só existe no email); índice único para localizar e consumir.
    @Indexed(unique = true)
    private String tokenHash;

    // Dono do token: usuário que recebeu o link.
    private String username;

    // Finalidade do token (LOGIN ou REGISTRO); impede usar um tipo no lugar do outro.
    private TipoToken tipo;

    // Fim da validade. É conferido pelo CÓDIGO (relógio da aplicação); não tem índice TTL.
    private Instant expiraEm;

    // Limpeza automática: o MongoDB apaga o documento depois desta data (expiraEm + folga de 1 dia).
    // A folga evita que um relógio desajustado no PC faça o Atlas apagar o token antes da hora.
    @Indexed(expireAfterSeconds = 0)
    private Instant apagarEm;
}
