// ============================================================
// ConfirmacaoController.java
// Autor: Miguel Santana
// Descrição: Endpoints dos links enviados por email.
//            O token no query string é a credencial.
// ============================================================
//
package com.pfc.thindesk.controller;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pfc.thindesk.entity.TipoToken;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import com.pfc.thindesk.security.JwtUtil;
import com.pfc.thindesk.service.EmailTokenService;
import com.pfc.thindesk.service.UsuarioService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Destino dos links enviados por email (rotas públicas: o token é a
 * credencial).
 * - GET /auth/confirmar?token=... -> conclui o LOGIN (seta o cookie final e vai
 * para "/")
 * - GET /auth/confirmar-registro?token=... -> ativa a conta (vai para
 * /login?confirmado=1)
 * Falhas caem em /login?erro=link-invalido (sem detalhes para o usuário); o
 * MOTIVO real
 * é registrado no log do servidor.
 */
@Controller
@RequestMapping("/auth")
public class ConfirmacaoController {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoController.class);

    // Cookie do token preAuth, apagado ao concluir o login.
    private static final String COOKIE_PRE = "thindesk_pre";

    // Cookie HttpOnly com o token FINAL (usuário autenticado de fato).
    private static final String COOKIE_AUTH = "thindesk_auth";

    // Consome (apaga, de forma atômica) o token e devolve o dono.
    @Autowired
    private EmailTokenService emailTokenService;

    // Carrega o usuário dono do token.
    @Autowired
    private UsuarioRepository usuarioRepository;

    // Ativa a conta no fluxo de confirmação de cadastro.
    @Autowired
    private UsuarioService usuarioService;

    // Emite o JWT final depois que o link é validado.
    @Autowired
    private JwtUtil jwtUtil;

    // Validade do cookie de autenticação, em milissegundos.
    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // Link de login: consome o token, grava o cookie final e vai para "/"; se
    // inválido, /login?erro.
    @GetMapping("/confirmar")
    public String confirmarLogin(@RequestParam(required = false) String token, HttpServletResponse response) {

        Optional<String> dono = emailTokenService.consumir(token, TipoToken.LOGIN); // uso único
        if (dono.isEmpty()) {
            log.warn("Link de LOGIN recusado: token inexistente, vencido ou já usado "
                    + "(email antigo? link já aberto? relógio do PC?)");
            return "redirect:/login?erro=link-invalido";
        }

        Optional<Usuario> usuario = usuarioRepository.findByUsername(dono.get());
        if (usuario.isEmpty() || !usuario.get().isAtivo()) {
            log.warn("Link de LOGIN recusado: usuário '{}' não existe ou está inativo", dono.get());
            return "redirect:/login?erro=link-invalido";
        }

        // Aqui, e só aqui, nasce o token FINAL (preAuth=false) em cookie HttpOnly
        String finalToken = jwtUtil.gerarToken(usuario.get().getUsername(), false);
        ResponseCookie auth = ResponseCookie.from(COOKIE_AUTH, finalToken)
                .httpOnly(true)
                .secure(false) // secure(true) em produção (HTTPS)
                .path("/")
                .maxAge(Duration.ofMillis(expirationMs))
                .sameSite("Lax")
                .build();
        ResponseCookie limpaPre = ResponseCookie.from(COOKIE_PRE, "")
                .httpOnly(true).path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, auth.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, limpaPre.toString());

        log.info("Login concluído pelo link do email: usuário '{}'", usuario.get().getUsername());
        return "redirect:/";
    }

    // Link de cadastro: ativa a conta e vai para /login?confirmado=1; se inválido,
    // /login?erro.
    @GetMapping("/confirmar-registro")
    public String confirmarRegistro(@RequestParam(required = false) String token) {
        boolean ok = usuarioService.confirmarRegistro(token);
        if (!ok) {
            log.warn("Link de CADASTRO recusado: token inexistente, vencido ou já usado");
        }
        return ok ? "redirect:/login?confirmado=1" : "redirect:/login?erro=link-invalido";
    }
}
