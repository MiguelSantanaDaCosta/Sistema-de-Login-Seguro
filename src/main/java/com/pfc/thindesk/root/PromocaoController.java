package com.pfc.thindesk.root;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import com.pfc.thindesk.security.bot.BotGuard;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Desafios de promoção (exigem login): Cavalo Verde (3 puzzles, 1900–2600) promove a TÉCNICO e
 * Torre Azul (5 puzzles, 2600–3000) promove a ADMIN. Mesmo tempo total e mesma proteção anti-bot da rainha.
 */
@Controller
public class PromocaoController {

    @Autowired
    private RootProps props;
    @Autowired
    private RootDesafioService service;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private BotGuard guard;

    /** Atributos da página genérica do desafio (root/desafio.html). */
    static Map<String, Object> visao(TipoDesafio t, RootProps props, String bloqueio) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("titulo", t.titulo);
        m.put("icone", t.icone);
        m.put("cor", t.cor);
        m.put("apiBase", t.apiBase);
        m.put("urlSucesso", t.urlSucesso);
        m.put("nPuzzles", props.puzzles(t));
        m.put("minutos", props.tempo(t) / 60);
        m.put("ratingMin", props.min(t));
        m.put("ratingMax", props.max(t));
        m.put("concede", t.concede == null ? null : rotulo(t.concede));
        m.put("bloqueio", bloqueio);
        return m;
    }

    static String rotulo(Role r) {
        return switch (r) {
            case ROLE_USUARIO -> "Usuário";
            case ROLE_TECNICO -> "Técnico";
            case ROLE_ADMIN -> "Administrador";
            case ROLE_ROOT -> "Root";
        };
    }

    @GetMapping("/desafio/{slug}")
    public ModelAndView pagina(@PathVariable String slug, Authentication auth,
            HttpServletRequest request, HttpServletResponse response) {
        TipoDesafio t = tipo(slug);
        String motivo = guard.avaliarCliente(request);
        if (motivo != null) {
            guard.bloquear(request, response, motivo);
            return null; // 418 já escrito
        }
        guard.emitirCookieVisita(request, response);
        String bloqueio = inelegivel(usuario(auth), t);
        return new ModelAndView("root/desafio").addAllObjects(visao(t, props, bloqueio));
    }

    @PostMapping("/api/desafio/{slug}/iniciar")
    @ResponseBody
    public ResponseEntity<?> iniciar(@PathVariable String slug, Authentication auth,
            @RequestParam(required = false) String website, @RequestParam(required = false) String wd,
            HttpServletRequest request, HttpServletResponse response) {
        TipoDesafio t = tipo(slug);
        String motivo = guard.avaliarChamadaApi(request, website, wd);
        if (motivo == null && guard.rajadaDeInicios(request)) {
            motivo = "rajada de inícios de desafio";
        }
        if (motivo != null) {
            guard.bloquear(request, response, motivo);
            return null;
        }
        Usuario u = usuario(auth);
        String bloqueio = inelegivel(u, t);
        if (bloqueio != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", bloqueio));
        }
        RootDesafioService.Resposta r = service.iniciar(t, u.getUsername());
        if (r.desafioId() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(t, r.desafioId(), props.tempo(t) + 60).toString());
        }
        return ResponseEntity.status(r.http()).body(r.corpo());
    }

    @PostMapping("/api/desafio/{slug}/lance")
    @ResponseBody
    public ResponseEntity<?> lance(@PathVariable String slug, Authentication auth, @RequestParam String lanceFen,
            @RequestParam(required = false) String website, @RequestParam(required = false) String wd,
            @CookieValue(name = "thindesk_desafio_cavalo", required = false) String cookieCavalo,
            @CookieValue(name = "thindesk_desafio_torre", required = false) String cookieTorre,
            HttpServletRequest request, HttpServletResponse response) {
        TipoDesafio t = tipo(slug);
        String motivo = guard.avaliarChamadaApi(request, website, wd);
        if (motivo != null) {
            guard.bloquear(request, response, motivo);
            return null;
        }
        Usuario u = usuario(auth);
        String bloqueio = inelegivel(u, t);
        if (bloqueio != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", bloqueio));
        }
        String desafioId = t == TipoDesafio.CAVALO ? cookieCavalo : cookieTorre;
        RootDesafioService.Resposta r = service.responder(t, u.getUsername(), desafioId, lanceFen);
        if (r.bot()) {
            guard.bloquear(request, response, "resposta rápida demais (< " + props.getMinSegundosPorPuzzle()
                    + "s) no " + t.titulo);
            return null;
        }
        Map<String, Object> corpo = r.corpo();
        if (r.concluido()) {
            // promove: o perfil vira o concedido (o mesmo efeito de o admin trocar o perfil), valendo já na próxima requisição
            Usuario atual = usuarioRepository.findByUsername(u.getUsername()).orElseThrow();
            Role de = maisAlto(atual);
            atual.setRoles(java.util.Set.of(t.concede));
            usuarioRepository.save(atual);
            org.slf4j.LoggerFactory.getLogger(PromocaoController.class)
                    .info("Promoção por desafio: usuário '{}' {} -> {} ({})", atual.getUsername(), de, t.concede, t.titulo);
            corpo = new LinkedHashMap<>();
            corpo.put("concluido", true);
            corpo.put("mensagem", "Desafio vencido! Você agora é " + rotulo(t.concede) + ".");
            corpo.put("redirect", "/?promovido=" + t.concede.name().substring(5).toLowerCase());
        }
        if (r.desafioId() == null) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(t, "", 0).toString());
        }
        return ResponseEntity.status(r.http()).body(corpo);
    }

    // ---- regras ----

    /** Motivo pelo qual o usuário não pode fazer o desafio; null se pode. */
    private String inelegivel(Usuario u, TipoDesafio t) {
        if (!props.habilitado(t)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (u.isContaDemo() || u.isPulaEmail()) {
            return "Contas de demonstração não podem ser promovidas por desafio.";
        }
        if (u.getRoles().contains(Role.ROLE_ROOT) || "root".equals(u.getUsername())) {
            return "Este usuário já tem o nível máximo.";
        }
        if (nivel(maisAlto(u)) >= nivel(t.concede)) {
            return "Você já é " + rotulo(maisAlto(u)) + " ou superior: este desafio não promove o seu perfil.";
        }
        return null;
    }

    private Role maisAlto(Usuario u) {
        Role melhor = Role.ROLE_USUARIO;
        for (Role r : u.getRoles()) {
            if (nivel(r) > nivel(melhor)) {
                melhor = r;
            }
        }
        return melhor;
    }

    private int nivel(Role r) {
        return switch (r) {
            case ROLE_USUARIO -> 0;
            case ROLE_TECNICO -> 1;
            case ROLE_ADMIN -> 2;
            case ROLE_ROOT -> 3;
        };
    }

    private Usuario usuario(Authentication auth) {
        Optional<Usuario> u = usuarioRepository.findByUsername(auth.getName());
        return u.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private TipoDesafio tipo(String slug) {
        TipoDesafio t = TipoDesafio.doSlug(slug);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return t;
    }

    private ResponseCookie cookie(TipoDesafio t, String valor, long segundos) {
        return ResponseCookie.from(t.cookie, valor).httpOnly(true).secure(false).path("/")
                .sameSite("Lax").maxAge(segundos).build();
    }
}
