package com.pfc.thindesk.controller;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.pfc.thindesk.entity.TipoToken;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import com.pfc.thindesk.security.bot.BotGuard;
import com.pfc.thindesk.service.EmailService;
import com.pfc.thindesk.service.EmailTokenService;
import com.pfc.thindesk.service.PuzzleService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * "Esqueci minha senha":
 *  1) GET  /esqueci-senha               -> página
 *  2) POST /api/auth/esqueci-senha      -> pede o link por email (SEMPRE a mesma resposta: não revela quem tem conta)
 *  3) GET  /redefinir-senha?token=...   -> página para digitar a nova senha
 *  4) POST /api/auth/redefinir-senha    -> consome o token (uso único) e grava a nova senha (BCrypt)
 *
 * Conta ainda não confirmada: o pedido reenvia o link de CONFIRMAÇÃO do cadastro. Contas demo, o usuário root e
 * contas desativadas por um admin não participam. O login continua em 3 fatores depois da troca de senha.
 */
@Controller
public class SenhaController {

    private static final Logger log = LoggerFactory.getLogger(SenhaController.class);

    static final String RESPOSTA_GENERICA = "Se existir uma conta com esse email, enviamos um link para continuar. "
            + "Confira também a caixa de spam.";

    // intervalo mínimo entre dois emails do mesmo tipo para a mesma conta (anti-spam de caixa de entrada)
    private static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);
    // Limites por IP em 10 minutos, separados por operação. Folgados de propósito: muita gente divide o mesmo IP
    // (campus, rede móvel). O anti-spam de verdade é o intervalo mínimo por CONTA (INTERVALO_REENVIO).
    private static final int MAX_ESQUECI_POR_IP = 15;
    private static final int MAX_REDEFINIR_POR_IP = 30;
    private static final long JANELA_MS = 600_000;

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EmailTokenService emailTokenService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PuzzleService puzzleService;
    @Autowired
    private BotGuard guard;

    /** SOMENTE testes automatizados: devolve o link no JSON (nunca em produção). */
    @Value("${app.dev.expor-link-confirmacao:false}")
    private boolean exporLinkDev;

    // o email sai em segundo plano: a resposta tem o mesmo tempo exista a conta ou não (sem vazar por latência)
    private final ExecutorService envio = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "email-senha");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, Deque<Long>> porIp = new ConcurrentHashMap<>();

    @GetMapping("/esqueci-senha")
    public String paginaEsqueci() {
        return "esqueci-senha";
    }

    @GetMapping("/redefinir-senha")
    public String paginaRedefinir(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        return "redefinir-senha";
    }

    @PostMapping("/api/auth/esqueci-senha")
    @ResponseBody
    public ResponseEntity<?> esqueci(@RequestParam String email, HttpServletRequest request) {
        if (limitado(request, "esqueci", MAX_ESQUECI_POR_IP)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("erro", "Muitos pedidos. Aguarde alguns minutos e tente de novo."));
        }
        String mail = email == null ? "" : email.trim().toLowerCase();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("mensagem", RESPOSTA_GENERICA);

        if (exporLinkDev) {
            corpo.put("linkDev", processar(mail)); // teste: síncrono, devolve o link
        } else {
            envio.submit(() -> {
                try {
                    processar(mail);
                } catch (RuntimeException e) {
                    log.error("Falha no esqueci-senha: {}", e.toString());
                }
            });
        }
        return ResponseEntity.ok(corpo);
    }

    /** Decide o que enviar para o email; devolve o link gerado (ou null). Nunca lança para o usuário. */
    private String processar(String mail) {
        if (!mail.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return null;
        }
        Optional<Usuario> opt = usuarioRepository.findByEmail(mail);
        if (opt.isEmpty()) {
            return null;
        }
        Usuario u = opt.get();
        if (u.isContaDemo() || u.isPulaEmail() || "root".equals(u.getUsername())
                || u.getEmail().endsWith("@thindesk.local") || u.getEmail().endsWith("@demo.thindesk.local")) {
            return null; // sem caixa de entrada real
        }
        if (!u.isEmailConfirmado()) {
            // cadastro nunca confirmado: reenvia o link de confirmação
            if (emailTokenService.gereiHa(u.getUsername(), TipoToken.REGISTRO, INTERVALO_REENVIO)) {
                return null;
            }
            String token = emailTokenService.gerar(u.getUsername(), TipoToken.REGISTRO);
            emailService.enviarTokenRegistro(u.getEmail(), token);
            return emailService.linkRegistro(token);
        }
        if (!u.isAtivo()) {
            return null; // desativada por um administrador
        }
        if (emailTokenService.gereiHa(u.getUsername(), TipoToken.REDEFINICAO, INTERVALO_REENVIO)) {
            return null;
        }
        String token = emailTokenService.gerar(u.getUsername(), TipoToken.REDEFINICAO);
        emailService.enviarTokenRedefinicao(u.getEmail(), token);
        return emailService.linkRedefinicao(token);
    }

    @PostMapping("/api/auth/redefinir-senha")
    @ResponseBody
    public ResponseEntity<?> redefinir(@RequestParam String token, @RequestParam String password,
            HttpServletRequest request) {
        if (limitado(request, "redefinir", MAX_REDEFINIR_POR_IP)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("erro", "Muitas tentativas. Aguarde alguns minutos e tente de novo."));
        }
        if (password == null || password.length() < 6 || password.length() > 72) {
            return ResponseEntity.badRequest().body(Map.of("erro", "A senha deve ter entre 6 e 72 caracteres."));
        }
        String invalido = "Link inválido, vencido ou já utilizado. Peça um novo em \"Esqueci minha senha\".";

        Optional<String> dono = emailTokenService.consumir(token, TipoToken.REDEFINICAO); // uso único
        if (dono.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", invalido));
        }
        Optional<Usuario> opt = usuarioRepository.findByUsername(dono.get());
        if (opt.isEmpty() || !opt.get().isAtivo() || opt.get().isContaDemo() || opt.get().isPulaEmail()) {
            return ResponseEntity.badRequest().body(Map.of("erro", invalido));
        }
        Usuario u = opt.get();
        u.setPassword(passwordEncoder.encode(password)); // BCrypt
        puzzleService.limpar(u);                          // zera o puzzle em andamento e salva o usuário
        emailTokenService.invalidar(u.getUsername(), TipoToken.LOGIN); // links de login antigos deixam de valer
        log.info("Senha redefinida pelo link do email: usuário '{}'", u.getUsername());
        String email = u.getEmail();
        envio.submit(() -> emailService.enviarSenhaAlterada(email));
        return ResponseEntity.ok(Map.of("mensagem", "Senha redefinida! Entre com a nova senha."));
    }

    // no máximo "max" chamadas por janela, por IP e por operação (IP lido da conexão, nunca de header do cliente)
    private boolean limitado(HttpServletRequest request, String operacao, int max) {
        long agora = System.currentTimeMillis();
        Deque<Long> fila = porIp.computeIfAbsent(operacao + "|" + guard.ip(request), k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && agora - fila.peekFirst() > JANELA_MS) {
                fila.pollFirst();
            }
            fila.addLast(agora);
            return fila.size() > max;
        }
    }
}
