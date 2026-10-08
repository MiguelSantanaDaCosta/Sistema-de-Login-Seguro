package com.pfc.thindesk.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pfc.thindesk.entity.PuzzleXadrez;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.PuzzleXadrezRepository;
import com.pfc.thindesk.repository.UsuarioRepository;
import com.pfc.thindesk.security.JwtUtil;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
    private PuzzleXadrezRepository puzzleXadrezRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Value("${app.security.puzzle.max-tentativas:5}")
    private int maxTentativas;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // ---------------------------------------------------------------
    // LOGIN — devolve preAuth em header E em cookie (HttpOnly)
    // ---------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username,
            @RequestParam String password,
            HttpServletResponse response) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        List<PuzzleXadrez> puzzles = puzzleXadrezRepository.findAll();
        if (puzzles.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of(
                    "erro", "Nenhum puzzle cadastrado no sistema."));
        }
        PuzzleXadrez puzzle = puzzles.get((int) (Math.random() * puzzles.size()));

        usuarioRepository.findByUsername(username).ifPresent(u -> {
            u.setTentativasPuzzle(0);
            usuarioRepository.save(u);
        });

        String preAuthToken = jwtUtil.gerarToken(username, true);

        // Cookie HttpOnly — navegador manda sozinho nas próximas requisições
        ResponseCookie preCookie = ResponseCookie.from(COOKIE_PRE, preAuthToken)
                .httpOnly(true)
                .secure(false) // true em produção (HTTPS)
                .path("/")
                .maxAge(Duration.ofMillis(expirationMs))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, preCookie.toString());

        return ResponseEntity.ok()
                .header("Authorization", "Bearer " + preAuthToken)
                .body(Map.of(
                        "mensagem", "Credenciais válidas. Resolva o puzzle de xadrez para continuar.",
                        "puzzleId", puzzle.getId(),
                        "fen", puzzle.getFen(),
                        "dificuldade", puzzle.getDificuldade() == null ? "desconhecida" : puzzle.getDificuldade(),
                        "descricao", puzzle.getDescricao() == null ? "" : puzzle.getDescricao()));
    }

    // ---------------------------------------------------------------
    // RESOLVER PUZZLE — lê preAuth do header OU do cookie
    // ---------------------------------------------------------------
    @PostMapping("/resolver-puzzle")
    public ResponseEntity<?> resolverPuzzle(
            @RequestHeader(value = "Authorization", required = false) String preAuthHeader,
            @RequestParam String puzzleId,
            @RequestParam String lanceFen,
            HttpServletRequest request,
            HttpServletResponse response) {

        String token = extrairTokenDeHeaderOuCookie(preAuthHeader, request, COOKIE_PRE);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "erro", "Sessão de pré-autenticação ausente. Faça login novamente."));
        }
        if (!jwtUtil.isTokenValido(token)) {
            return ResponseEntity.status(401).body(Map.of(
                    "erro", "Token expirado ou inválido. Faça login novamente."));
        }
        if (!Boolean.TRUE.equals(jwtUtil.extrairPreAuth(token))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Token fornecido não é preAuth."));
        }

        String username = jwtUtil.extrairUsername(token);
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + username));

        Optional<PuzzleXadrez> puzzleOpt = puzzleXadrezRepository.findById(puzzleId);
        if (puzzleOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Puzzle não encontrado."));
        }
        PuzzleXadrez puzzle = puzzleOpt.get();

        String lance = lanceFen == null ? "" : lanceFen.trim().toLowerCase();
        String esperado = puzzle.getLanceCorreto() == null ? "" : puzzle.getLanceCorreto().trim().toLowerCase();

        // --- Acertou ---
        if (!esperado.isEmpty() && lance.equals(esperado)) {
            usuario.setTentativasPuzzle(0);
            usuarioRepository.save(usuario);

            String finalToken = jwtUtil.gerarToken(username, false);

            // Seta cookie de sessão
            ResponseCookie authCookie = ResponseCookie.from(COOKIE_AUTH, finalToken)
                    .httpOnly(true).secure(false).path("/")
                    .maxAge(Duration.ofMillis(expirationMs))
                    .sameSite("Lax").build();
            // Apaga o preAuth (já foi usado)
            ResponseCookie clearPre = ResponseCookie.from(COOKIE_PRE, "")
                    .httpOnly(true).path("/").maxAge(0).build();

            response.addHeader(HttpHeaders.SET_COOKIE, authCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, clearPre.toString());

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + finalToken)
                    .body(Map.of(
                            "mensagem", "Login concluído.",
                            "authToken", finalToken));
        }

        // --- Errou ---
        int tentativas = usuario.getTentativasPuzzle() + 1;
        usuario.setTentativasPuzzle(tentativas);

        if (tentativas >= maxTentativas) {
            usuario.setTentativasPuzzle(0);
            usuarioRepository.save(usuario);
            return ResponseEntity.status(429).body(Map.of(
                    "erro", "Número máximo de tentativas excedido. Faça login novamente.",
                    "tentativas", tentativas,
                    "maxTentativas", maxTentativas));
        }

        usuarioRepository.save(usuario);
        return ResponseEntity.status(403).body(Map.of(
                "erro", "Lance incorreto.",
                "tentativas", tentativas,
                "restantes", maxTentativas - tentativas));
    }

    // ---------------------------------------------------------------
    // LOGOUT — apaga os dois cookies
    // ---------------------------------------------------------------
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        ResponseCookie clearAuth = ResponseCookie.from(COOKIE_AUTH, "")
                .httpOnly(true).path("/").maxAge(0).build();
        ResponseCookie clearPre = ResponseCookie.from(COOKIE_PRE, "")
                .httpOnly(true).path("/").maxAge(0).build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearAuth.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearPre.toString());

        return ResponseEntity.ok(Map.of("mensagem", "Logout efetuado."));
    }

    // ---------------------------------------------------------------
    // Helper: extrai token do header Authorization ou de um cookie
    // ---------------------------------------------------------------
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
}
