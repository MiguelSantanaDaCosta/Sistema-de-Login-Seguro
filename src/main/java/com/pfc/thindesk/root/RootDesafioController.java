package com.pfc.thindesk.root;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import com.pfc.thindesk.security.JwtUtil;

import org.springframework.web.servlet.ModelAndView;
import com.pfc.thindesk.security.bot.BotGuard;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Rotas PÚBLICAS do desafio da rainha vermelha. Ao vencer, emite o cookie de sessão do usuário "root". */
@Controller
public class RootDesafioController {

    private static final String COOKIE_DESAFIO = "thindesk_root";
    private static final String COOKIE_AUTH = "thindesk_auth";

    @Autowired
    private RootProps props;
    @Autowired
    private RootDesafioService service;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private BotGuard guard;

    /** Duração da sessão root (a mesma do JWT). */
    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @GetMapping("/root/desafio")
    public ModelAndView pagina(HttpServletRequest request, HttpServletResponse response) {
        exigirHabilitado();
        String motivo = guard.avaliarCliente(request);
        if (motivo != null) {
            guard.bloquear(request, response, motivo);
            return null; // resposta 418 já escrita
        }
        guard.emitirCookieVisita(request, response); // prova de que o cliente passou pela página
        return new ModelAndView("root/desafio");
    }

    @PostMapping("/api/root/desafio/iniciar")
    @ResponseBody
    public ResponseEntity<?> iniciar(@RequestParam(required = false) String website,
            @RequestParam(required = false) String wd,
            HttpServletRequest request, HttpServletResponse response) {
        exigirHabilitado();
        String motivo = guard.avaliarChamadaApi(request, website, wd);
        if (motivo == null && guard.rajadaDeInicios(request)) {
            motivo = "rajada de inícios de desafio";
        }
        if (motivo != null) {
            guard.bloquear(request, response, motivo);
            return null;
        }
        RootDesafioService.Resposta r = service.iniciar();
        if (r.desafioId() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookieDesafio(r.desafioId(), props.getTempoSegundos() + 60).toString());
        }
        return ResponseEntity.status(r.http()).body(r.corpo());
    }

    @PostMapping("/api/root/desafio/lance")
    @ResponseBody
    public ResponseEntity<?> lance(@RequestParam String lanceFen,
            @RequestParam(required = false) String website,
            @RequestParam(required = false) String wd,
            @CookieValue(name = COOKIE_DESAFIO, required = false) String desafioId,
            HttpServletRequest request, HttpServletResponse response) {
        exigirHabilitado();
        String motivo = guard.avaliarChamadaApi(request, website, wd);
        if (motivo != null) {
            guard.bloquear(request, response, motivo);
            return null;
        }
        RootDesafioService.Resposta r = service.responder(desafioId, lanceFen);
        if (r.bot()) {
            guard.bloquear(request, response, "resposta rápida demais (< " + props.getMinSegundosPorPuzzle()
                    + "s) em puzzle de rating alto");
            return null;
        }

        if (r.concluido()) {
            Usuario root = usuarioRepository.findByUsername("root")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuário root ausente"));
            List<String> roles = root.getRoles().stream().map(Role::name).toList();
            String token = jwtUtil.gerarToken("root", false, roles);
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_AUTH, token)
                    .httpOnly(true).secure(false).path("/").sameSite("Lax")
                    .maxAge(Duration.ofMillis(expirationMs)).build().toString());
        }
        if (r.desafioId() == null) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookieDesafio("", 0).toString());
        }
        return ResponseEntity.status(r.http()).body(r.corpo());
    }

    private ResponseCookie cookieDesafio(String valor, long segundos) {
        return ResponseCookie.from(COOKIE_DESAFIO, valor).httpOnly(true).secure(false).path("/")
                .sameSite("Lax").maxAge(segundos).build();
    }

    private void exigirHabilitado() {
        if (!props.isHabilitado()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
