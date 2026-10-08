package com.pfc.thindesk.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import com.pfc.thindesk.service.EmailService;
import com.pfc.thindesk.service.UsuarioService;

import jakarta.validation.Valid;

//Cadastro público em 2 etapas.
//  - GET  /registrar           -> página HTML
//  - POST /api/auth/registrar  -> etapa 1: cria usuário PENDENTE e envia o email
@Controller
public class RegistroController {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private EmailService emailService;

    /** SOMENTE desenvolvimento/testes: devolve o link do email no JSON. */
    @Value("${app.dev.expor-link-confirmacao:false}")
    private boolean exporLinkDev;

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

        String token;
        try {
            token = usuarioService.registrar(req);
        } catch (ConflitoCadastroException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
        } catch (DuplicateKeyException e) {
            // Corrida entre duas requisições: o índice único do Mongo barrou a segunda
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("erro", "Nome de usuário ou email já cadastrado."));
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("mensagem", "Cadastro recebido! Enviamos um link de confirmação para o seu email. "
                + "Clique nele para ativar a conta.");
        corpo.put("aguardandoConfirmacao", true);
        if (exporLinkDev) {
            corpo.put("linkConfirmacao", emailService.linkRegistro(token));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(corpo);
    }

    /** JSON malformado ou corpo ausente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ResponseEntity<?> corpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Corpo da requisição inválido (JSON esperado)."));
    }
}
