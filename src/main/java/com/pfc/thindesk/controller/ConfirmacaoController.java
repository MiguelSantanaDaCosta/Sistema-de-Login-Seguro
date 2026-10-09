// ============================================================
// ConfirmacaoController.java
// Autor: Miguel Santana
// Descrição: Endpoints dos links enviados por email.
//            O token no query string é a credencial.
// ============================================================
package com.pfc.thindesk.controller;

import java.time.Duration;
import java.util.Optional;

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

// Controller dos links de confirmação enviados por e-mail.
@Controller
@RequestMapping("/auth")
public class ConfirmacaoController {

    // pre = senha ok, puzzle pendente
    // auth = autenticado de fato
    private static final String COOKIE_PRE  = "thindesk_pre";
    private static final String COOKIE_AUTH = "thindesk_auth";

    @Autowired private EmailTokenService emailTokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private JwtUtil jwtUtil;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // Link de LOGIN: gera o token final
    // Link de LOGIN: consome o token e emite o JWT final (cookie thindesk_auth).
    @GetMapping("/confirmar")
    public String confirmarLogin(@RequestParam(required = false) String token, HttpServletResponse response) {

        // Consome o token (uso único)
        Optional<String> dono = emailTokenService.consumir(token, TipoToken.LOGIN);
        if (dono.isEmpty()) {
            return "redirect:/login?erro=link-invalido";
        }

        Optional<Usuario> usuario = usuarioRepository.findByUsername(dono.get());
        if (usuario.isEmpty() || !usuario.get().isAtivo()) {
            return "redirect:/login?erro=link-invalido";
        }

        // Aqui nasce o token final (preAuth=false)
        String finalToken = jwtUtil.gerarToken(usuario.get().getUsername(), false);

        ResponseCookie auth = ResponseCookie.from(COOKIE_AUTH, finalToken)
                .httpOnly(true)
                .secure(false) // trocar para true em produção (HTTPS)
                .path("/")
                .maxAge(Duration.ofMillis(expirationMs))
                .sameSite("Lax")
                .build();

        // preAuth já cumpriu o papel
        ResponseCookie limpaPre = ResponseCookie.from(COOKIE_PRE, "")
                .httpOnly(true).path("/").maxAge(0).build();

        response.addHeader(HttpHeaders.SET_COOKIE, auth.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, limpaPre.toString());

        return "redirect:/";
    }

    // Link de CADASTRO: ativa a conta
    // Link de CADASTRO: ativa a conta ao clicar.
    @GetMapping("/confirmar-registro")
    public String confirmarRegistro(@RequestParam(required = false) String token) {
        return usuarioService.confirmarRegistro(token)
                ? "redirect:/login?confirmado=1"
                : "redirect:/login?erro=link-invalido";
    }
}
