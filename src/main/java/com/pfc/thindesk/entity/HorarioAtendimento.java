// ============================================================
// HorarioAtendimento.java
// Autor: Miguel Santana
// Descrição: Horário de atendimento por setor (dia da semana + faixa início/fim).
// ============================================================
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
    @jakarta.validation.constraints.NotBlank(message = "Informe o setor.")
    @jakarta.validation.constraints.Size(max = 100)
    private String setor;
    @jakarta.validation.constraints.Pattern(regexp = "Segunda-feira|Terça-feira|Quarta-feira|Quinta-feira|Sexta-feira|Sábado|Domingo",
            message = "Selecione um dia da semana.")
    @jakarta.validation.constraints.NotBlank
    private String diaSemana; // Exemplo: "Segunda-feira"
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = "Informe um horário inicial válido.")
    private String horarioInicio; // Exemplo: "08:00"
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = "Informe um horário final válido.")
    private String horarioFim; // Exemplo: "18:00"
}
