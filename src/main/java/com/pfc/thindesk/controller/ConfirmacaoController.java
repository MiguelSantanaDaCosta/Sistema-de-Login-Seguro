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


// Destino dos links enviados por email (rotas públicas: o token é a credencial).
//  - GET /auth/confirmar?token=...           -> conclui o LOGIN (seta o cookie final e vai para "/")
//  - GET /auth/confirmar-registro?token=...  -> ativa a conta (vai para /login?confirmado=1)
@Controller
@RequestMapping("/auth")
public class ConfirmacaoController {

    private static final String COOKIE_PRE = "thindesk_pre";
    private static final String COOKIE_AUTH = "thindesk_auth";

    @Autowired
    private EmailTokenService emailTokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @GetMapping("/confirmar")
    public String confirmarLogin(@RequestParam(required = false) String token, HttpServletResponse response) {
        Optional<String> dono = emailTokenService.consumir(token, TipoToken.LOGIN); // uso único
        if (dono.isEmpty()) {
            return "redirect:/login?erro=link-invalido";
        }
        Optional<Usuario> usuario = usuarioRepository.findByUsername(dono.get());
        if (usuario.isEmpty() || !usuario.get().isAtivo()) {
            return "redirect:/login?erro=link-invalido";
        }

        // Aqui, e só aqui, nasce o token FINAL (cookie HttpOnly)
        String finalToken = jwtUtil.gerarToken(usuario.get().getUsername(), false);
        ResponseCookie auth = ResponseCookie.from(COOKIE_AUTH, finalToken)
                .httpOnly(true).secure(false) // secure(true) em produção (HTTPS)
                .path("/").maxAge(Duration.ofMillis(expirationMs)).sameSite("Lax").build();
        ResponseCookie limpaPre = ResponseCookie.from(COOKIE_PRE, "")
                .httpOnly(true).path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, auth.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, limpaPre.toString());

        return "redirect:/";
    }

    @GetMapping("/confirmar-registro")
    public String confirmarRegistro(@RequestParam(required = false) String token) {
        return usuarioService.confirmarRegistro(token)
                ? "redirect:/login?confirmado=1"
                : "redirect:/login?erro=link-invalido";
    }
}
