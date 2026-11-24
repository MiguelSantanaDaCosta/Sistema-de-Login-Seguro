// Descrição: Representação do chamado devolvido pelo ms-chamados.
package com.pfc.thindesk.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChamadoMs(
        String id,
        String titulo,
        String descricao,
        String solicitante,
        String status,
        Instant dataAbertura,
        String solucao,
        String tecnico,
        Instant dataResolucao) {

    // Datas já no fuso do servidor, para o #temporals do Thymeleaf
    public LocalDateTime abertura() {
        return dataAbertura == null ? null : LocalDateTime.ofInstant(dataAbertura, ZoneId.systemDefault());
    }

    public LocalDateTime resolucao() {
        return dataResolucao == null ? null : LocalDateTime.ofInstant(dataResolucao, ZoneId.systemDefault());
    }
}
