package com.pfc.thindesk.controller;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pfc.thindesk.entity.PuzzleXadrez;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import com.pfc.thindesk.security.JwtUtil;
import com.pfc.thindesk.service.PuzzleService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticação em duas etapas:
 *  1) /login            -> valida a senha, emite token preAuth e o 1º puzzle (rating <= máximo)
 *  2) /resolver-puzzle  -> valida o lance contra o puzzle GUARDADO NO SERVIDOR
 *     - acertou: emite o token final
 *     - 5 erros ou tempo esgotado: gera outro puzzle (até o limite por sessão)
 *  3) /novo-puzzle      -> o navegador chama quando o cronômetro zera (o servidor confere o prazo)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_PRE = "thindesk_pre";
    private static final String COOKIE_AUTH = "thindesk_auth";

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PuzzleService puzzleService;

    @Value("${app.security.puzzle.max-tentativas:5}")
    private int maxTentativas;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // ---------------------------------------------------------------
    // LOGIN — devolve preAuth em header E em cookie (HttpOnly) + 1º puzzle
    // ---------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username,
            @RequestParam String password,
            HttpServletResponse response) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        PuzzleXadrez puzzle = puzzleService.iniciarSessao(usuario);

        String preAuthToken = jwtUtil.gerarToken(username, true);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieSessao(COOKIE_PRE, preAuthToken).toString());

        Map<String, Object> corpo = mapa("mensagem",
                "Credenciais válidas. Resolva o puzzle de xadrez para continuar.");
        corpo.putAll(puzzleService.dados(puzzle, usuario));

        return ResponseEntity.ok()
                .header("Authorization", "Bearer " + preAuthToken)
                .body(corpo);
    }

    // ---------------------------------------------------------------
    // RESOLVER PUZZLE
    // O puzzle vem do servidor (usuario.puzzleAtualId); o "puzzleId" do cliente é opcional
    // e, se enviado, precisa bater com o puzzle ativo.
    // ---------------------------------------------------------------
    @PostMapping("/resolver-puzzle")
    public ResponseEntity<?> resolverPuzzle(
            @RequestHeader(value = "Authorization", required = false) String preAuthHeader,
            @RequestParam String lanceFen,
            @RequestParam(required = false) String puzzleId,
            HttpServletRequest request,
            HttpServletResponse response) {

        Usuario usuario = usuarioDoPreAuth(preAuthHeader, request);

        Optional<PuzzleXadrez> atualOpt = puzzleService.atual(usuario);
        if (atualOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapa(
                    "erro", "Nenhum puzzle ativo. Faça login novamente."));
        }
        PuzzleXadrez puzzle = atualOpt.get();

        if (puzzleId != null && !puzzleId.equals(puzzle.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapa(
                    "erro", "Este puzzle não é mais o ativo.",
                    "novoPuzzle", puzzleService.dados(puzzle, usuario)));
        }

        // Prazo esgotado: gera outro puzzle (a tentativa não conta como erro)
        if (puzzleService.expirou(usuario)) {
            return rotacionar(usuario, HttpStatus.GONE,
                    "O tempo acabou. Um novo puzzle foi gerado.", response);
        }

        String lance = lanceFen == null ? "" : lanceFen.trim().toLowerCase();
        String esperado = puzzle.getLanceCorreto() == null ? "" : puzzle.getLanceCorreto().trim().toLowerCase();

        // --- Acertou ---
        if (!esperado.isEmpty() && lance.equals(esperado)) {
            puzzleService.limpar(usuario);

            String username = usuario.getUsername();
            String finalToken = jwtUtil.gerarToken(username, false);

            response.addHeader(HttpHeaders.SET_COOKIE, cookieSessao(COOKIE_AUTH, finalToken).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookieLimpo(COOKIE_PRE).toString()); // preAuth já usado

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + finalToken)
                    .body(mapa("mensagem", "Login concluído.", "authToken", finalToken));
        }

        // --- Errou ---
        int tentativas = usuario.getTentativasPuzzle() + 1;
        usuario.setTentativasPuzzle(tentativas);

        if (tentativas >= maxTentativas) {
            return rotacionar(usuario, HttpStatus.FORBIDDEN,
                    "Você errou " + maxTentativas + " vezes. Um novo puzzle foi gerado.", response);
        }

        usuarioRepository.save(usuario);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapa(
                "erro", "Lance incorreto.",
                "tentativas", tentativas,
                "restantes", maxTentativas - tentativas));
    }

    // NOVO PUZZLE quando o cronômetro zera
    // O servidor confere o prazo
    @PostMapping("/novo-puzzle")
    public ResponseEntity<?> novoPuzzle(
            @RequestHeader(value = "Authorization", required = false) String preAuthHeader,
            HttpServletRequest request,
            HttpServletResponse response) {

        Usuario usuario = usuarioDoPreAuth(preAuthHeader, request);

        Optional<PuzzleXadrez> atualOpt = puzzleService.atual(usuario);
        if (atualOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapa(
                    "erro", "Nenhum puzzle ativo. Faça login novamente."));
        }

        // Ainda dentro do prazo (relógio do cliente adiantado): devolve o mesmo puzzle
        if (!puzzleService.expirou(usuario)) {
            Map<String, Object> corpo = puzzleService.dados(atualOpt.get(), usuario);
            corpo.put("renovado", false);
            return ResponseEntity.ok(corpo);
        }

        try {
            PuzzleXadrez novo = puzzleService.renovar(usuario);
            Map<String, Object> corpo = puzzleService.dados(novo, usuario);
            corpo.put("renovado", true);
            return ResponseEntity.ok(corpo);
        } catch (PuzzleService.LimiteAtingidoException e) {
            return limiteAtingido(response);
        }
    }

    // LOGOUT — apaga os dois cookies
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieLimpo(COOKIE_AUTH).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieLimpo(COOKIE_PRE).toString());
        return ResponseEntity.ok(mapa("mensagem", "Logout efetuado."));
    }

    // Tratamento de erros
    static class PreAuthInvalidoException extends RuntimeException {
        final int status;

        PreAuthInvalidoException(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    @ExceptionHandler(PreAuthInvalidoException.class)
    public ResponseEntity<?> preAuthInvalido(PreAuthInvalidoException e) {
        return ResponseEntity.status(e.status).body(mapa("erro", e.getMessage()));
    }

    /** Senha errada, usuário inexistente ou desativado — mesma resposta para não vazar qual foi. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> credenciaisInvalidas(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapa("erro", "Credenciais inválidas."));
    }

    @ExceptionHandler(PuzzleService.SemPuzzlesException.class)
    public ResponseEntity<?> semPuzzles(PuzzleService.SemPuzzlesException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapa("erro", e.getMessage()));
    }

    // Helpers

    /** Gera outro puzzle e responde com {erro, novoPuzzle}; ou 429 se estourou o limite da sessão. */
    private ResponseEntity<?> rotacionar(Usuario usuario, HttpStatus status, String erro,
            HttpServletResponse response) {
        try {
            PuzzleXadrez novo = puzzleService.renovar(usuario);
            return ResponseEntity.status(status).body(mapa(
                    "erro", erro,
                    "novoPuzzle", puzzleService.dados(novo, usuario)));
        } catch (PuzzleService.LimiteAtingidoException e) {
            return limiteAtingido(response);
        }
    }

    private ResponseEntity<?> limiteAtingido(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieLimpo(COOKIE_PRE).toString());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapa(
                "erro", "Limite de puzzles atingido. Faça login novamente."));
    }

    /** Valida o token preAuth (header ou cookie) e devolve o usuário dono dele. */
    private Usuario usuarioDoPreAuth(String header, HttpServletRequest request) {
        String token = extrairTokenDeHeaderOuCookie(header, request, COOKIE_PRE);
        if (token == null) {
            throw new PreAuthInvalidoException(401, "Sessão de pré-autenticação ausente. Faça login novamente.");
        }
        if (!jwtUtil.isTokenValido(token)) {
            throw new PreAuthInvalidoException(401, "Token expirado ou inválido. Faça login novamente.");
        }
        if (!Boolean.TRUE.equals(jwtUtil.extrairPreAuth(token))) {
            throw new PreAuthInvalidoException(400, "Token fornecido não é preAuth.");
        }
        String username = jwtUtil.extrairUsername(token);
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new PreAuthInvalidoException(401, "Usuário não encontrado."));
    }

    private ResponseCookie cookieSessao(String nome, String valor) {
        return ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(false) // true em produção (HTTPS)
                .path("/")
                .maxAge(Duration.ofMillis(expirationMs))
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie cookieLimpo(String nome) {
        return ResponseCookie.from(nome, "").httpOnly(true).path("/").maxAge(0).build();
    }

    private String extrairTokenDeHeaderOuCookie(String header, HttpServletRequest request, String cookieName) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (cookieName.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    /** Monta um JSON com a ordem das chaves preservada: mapa("a", 1, "b", 2). */
    private static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], pares[i + 1]);
        }
        return m;
    }
}
