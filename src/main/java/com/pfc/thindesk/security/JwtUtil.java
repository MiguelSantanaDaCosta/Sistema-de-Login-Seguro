// ============================================================
// JwtUtil.java
// Autor: Miguel Santana
// Descrição: Gera e valida JWT. Dois tipos: preAuth (senha ok,
//            puzzle pendente) e final (autenticado de fato).
// ============================================================
package com.pfc.thindesk.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// Utilitário para geração e validação de tokens JWT.
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // Deriva a chave HMAC do segredo
    private SecretKey getSigninKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Gera token com claim preAuth
    // Gera JWT com username e a claim preAuth.
    public String gerarToken(String username, boolean isPreAuth) {
        return Jwts.builder()
                .subject(username)
                .claim("preAuth", isPreAuth)
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + expirationMs))
                .signWith(getSigninKey())
                .compact();
    }

    // Extrai o username (subject) do token.
    public String extrairUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSigninKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // Extrai a claim preAuth.
    public Boolean extrairPreAuth(String token) {
        return Jwts.parser()
                .verifyWith(getSigninKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("preAuth", Boolean.class);
    }

    public boolean isTokenValido(String token) {
        try {
            Jwts.parser().verifyWith(getSigninKey()).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
