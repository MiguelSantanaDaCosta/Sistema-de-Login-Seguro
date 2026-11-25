// ============================================================
// Cliente.java
// Autor: Miguel Santana
// Descrição: Cliente atendido pelo sistema (nome, telefone, setor).
// ============================================================
package com.pfc.thindesk.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "clientes")
public class Cliente {

    @Id
    private String id;
    @jakarta.validation.constraints.NotBlank(message = "Informe o nome do cliente.")
    @jakarta.validation.constraints.Size(max = 100, message = "Nome: no máximo 100 caracteres.")
    private String nome;
    @jakarta.validation.constraints.Size(max = 30, message = "Telefone: no máximo 30 caracteres.")
    private String telefone;
    @jakarta.validation.constraints.NotBlank(message = "Informe o setor.")
    @jakarta.validation.constraints.Size(max = 100, message = "Setor: no máximo 100 caracteres.")
    private String setor;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getSetor() {
        return setor;
    }

    public void setSetor(String setor) {
        this.setor = setor;
    }

}
