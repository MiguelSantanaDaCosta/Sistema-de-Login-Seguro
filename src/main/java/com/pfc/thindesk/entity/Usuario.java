package com.pfc.thindesk.entity;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String nomeCompleto;

    /** false = conta pendente de confirmação por email OU desativada pelo admin. */
    private boolean ativo = true;

    /**
     * false somente enquanto o cadastro público aguarda a confirmação do email.
     * Contas antigas/seed (sem o campo) valem como confirmadas.
     */
    private boolean emailConfirmado = true;

    private Set<Role> roles = new HashSet<>();

    // --- Estado do 2FA (o servidor é a fonte da verdade; o cliente não escolhe o puzzle) ---
    /** Erros no puzzle atual. */
    private int tentativasPuzzle = 0;
    /** Id do puzzle que este usuário deve resolver agora (null = nenhum ativo). */
    private String puzzleAtualId;
    /** Instante (epoch em ms) em que o puzzle atual expira. */
    private Long puzzleExpiraEm;
    /** Quantos puzzles já foram emitidos neste login. */
    private int puzzlesNaSessao = 0;
}
