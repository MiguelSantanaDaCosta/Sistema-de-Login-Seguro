package com.pfc.thindesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload do endpoint POST /api/auth/registrar.
 * As regras de validação ficam aqui (Bean Validation) e são disparadas por @Valid no controller.
 */
// Payload do POST /api/auth/registrar (Bean Validation).
public class RegistroRequest {

    @NotBlank(message = "O nome de usuário é obrigatório.")
    @Size(min = 3, max = 30, message = "O nome de usuário deve ter entre 3 e 30 caracteres.")
    @Pattern(regexp = com.pfc.thindesk.util.NomeUsuario.REGEX,
             message = "Nome de usuário inválido: 3 a 30 caracteres com letras (com acento), números, espaço entre palavras e . _ - @ +")
    public String username;

    @NotBlank(message = "O email é obrigatório.")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
           message = "Formato de email inválido.")
    @Size(max = 120, message = "O email deve ter no máximo 120 caracteres.")
    public String email;

    // Máximo 72: o BCrypt ignora/rejeita bytes além disso
    @NotBlank(message = "A senha é obrigatória.")
    @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres.")
    public String password;

    @NotBlank(message = "O nome completo é obrigatório.")
    @Size(max = 100, message = "O nome completo deve ter no máximo 100 caracteres.")
    public String nomeCompleto;

    public void setUsername(String username) {
        this.username = com.pfc.thindesk.util.NomeUsuario.normalizar(username);
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto == null ? null : nomeCompleto.trim();
    }
}
