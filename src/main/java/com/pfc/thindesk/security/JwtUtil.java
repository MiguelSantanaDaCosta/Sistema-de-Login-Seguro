package com.pfc.thindesk.security;

import  io.jsonwebtoken.Jwts;
import  io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecreKey;
import java.util.Date;

@Component 
public class JwtUtil {
    //INJETA OS VALORES DEFINIDOS NO PROPERTIES
    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    //CONVERTE A STRING SECRETA EMN UMA CHAVE CRIPTOGRAFICA SHA

    private SecreKey getSigninKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    //GERA O TOKEN, O PARAMETRO DEFINE SE O PUZZLE ESTA PENDENTE
    public String gerarToken(String username, boolean isPreAuth) {
        return Jwts.builder()
            .subject(username)
            .claim("preAuth", isPreAuth)
            .issuedAt(new Date())
            .expiration(new Date(new Date().getTime() + expirationMs))
            .signWith(getSigninKey())
            .compact();

    }
    //ABRE O TOKEN E EXTRAI O NOME DO USUARIO
    public String extrairUsername(String token) {
        return Jwts.parser()
            .verifyWith(getSigninKey())
            .builde()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    //VALIDA SE O TOKEN NÃO ADULTERADO OU EXPIRADO
    public boolean isTokenValido(String token) {
        try {
            Jwts.parser().verifyWith(getSigninKey()).build().parseSignedClaims(token);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

}
