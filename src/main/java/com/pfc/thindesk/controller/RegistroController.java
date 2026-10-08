package com.pfc.thindesk.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.pfc.thindesk.dto.RegistroRequest;
import com.pfc.thindesk.exception.ConflitoCadastroException;
import com.pfc.thindesk.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * Cadastro público de usuários.
 * - GET /registrar -> página HTML (registrar.html)
 * - POST /api/auth/registrar -> JSON; sempre responde {"erro": "..."} em caso
 * de falha
 *
 * Códigos: 201 criado | 400 dados inválidos | 409 username/email já existe.
 */
@Controller
public class RegistroController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/registrar")
    public String pagina() {
        return "registrar";
    }

    @PostMapping("/api/auth/registrar")
    @ResponseBody
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroRequest req, BindingResult resultado) {

        if (resultado.hasErrors()) {
            Map<String, String> campos = new LinkedHashMap<>();
            resultado.getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
            String primeiro = campos.values().iterator().next();
            return ResponseEntity.badRequest().body(Map.of("erro", primeiro, "campos", campos));
        }

        try {
            usuarioService.registrar(req);
        } catch (ConflitoCadastroException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
        } catch (DuplicateKeyException e) {
            // Corrida entre duas requisições: o índice único do Mongo barrou a segunda
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("erro", "Nome de usuário ou email já cadastrado."));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("mensagem", "Cadastro realizado. Faça login para continuar."));
    }

    /** JSON malformado ou corpo ausente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ResponseEntity<?> corpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Corpo da requisição inválido (JSON esperado)."));
    }
}
