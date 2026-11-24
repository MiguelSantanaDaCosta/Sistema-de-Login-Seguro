package com.pfc.thindesk.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/** Estado de uma tentativa do desafio root (id aleatório guardado em cookie HttpOnly). */
@Document(collection = "root_desafios")
@Data
public class RootDesafio {

    @Id
    private String id;

    /** Id do puzzle que deve ser resolvido agora. */
    private String puzzleAtualId;

    /** Quantos puzzles já foram resolvidos em sequência. */
    private int resolvidos;

    /** Puzzles já usados nesta tentativa (não repete). */
    private List<String> usados = new ArrayList<>();

    /** Quando o puzzle atual foi entregue (epoch ms): resposta rápida demais denuncia bot. */
    private long puzzleEmitidoEm;

    /** Prazo final do desafio inteiro (epoch ms). */
    private long expiraEm;

    /** Limpeza automática de tentativas abandonadas. */
    @Indexed(expireAfter = "15m")
    private Instant criadoEm = Instant.now();
}
