package com.pfc.thindesk.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Catálogos auxiliares: tipos de chamado e recursos disponíveis para atendimento. */
@Data
@Document("catalogos")
public class ItemCatalogo {
    @Id
    private String id;
    private String categoria;
    @NotBlank(message = "Informe o nome.")
    @Size(max = 100, message = "Nome: no máximo 100 caracteres.")
    private String nome;
    @Size(max = 500, message = "Descrição: no máximo 500 caracteres.")
    private String descricao;
}
