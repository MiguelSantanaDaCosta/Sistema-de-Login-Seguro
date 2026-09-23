package com.pfc.thindesk.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "horariosAtendimento")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioAtendimento {

    @Id
    private String id;
    private String setor;
    private String diaSemana; // Exemplo: "Segunda-feira"
    private String horarioInicio; // Exemplo: "08:00"
    private String horarioFim; // Exemplo: "18:00"
}