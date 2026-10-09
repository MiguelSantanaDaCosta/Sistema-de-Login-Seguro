package com.pfc.thindesk.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.pfc.thindesk.entity.EmailToken;
import com.pfc.thindesk.entity.TipoToken;
import com.pfc.thindesk.repository.EmailTokenRepository;

/** Gera e consome tokens de uso único enviados por email (login e registro). */
@Service
public class EmailTokenService {

    // Gerador criptograficamente seguro usado para criar os tokens.
    private static final SecureRandom RANDOM = new SecureRandom();

    // Folga da limpeza automática (TTL) em relação à validade; tolera relógios
    // desajustados.
    private static final Duration FOLGA_LIMPEZA = Duration.ofDays(1);

    // Persistência dos tokens (apenas o hash).
    @Autowired
    private EmailTokenRepository repository;

    // Usado no findAndRemove atômico que garante o uso único do token.
    @Autowired
    private MongoTemplate mongoTemplate;

    // Validade do link de login, em minutos.
    @Value("${app.security.email-token.login-minutos:15}")
    private long loginMinutos;

    // Validade do link de confirmação de cadastro, em horas.
    @Value("${app.security.email-token.registro-horas:24}")
    private long registroHoras;

    /**
     * Gera um token novo e devolve o valor BRUTO (vai só no email). Tokens
     * anteriores
     * do mesmo usuário e tipo são apagados: apenas o último link enviado vale.
     */
    public String gerar(String username, TipoToken tipo) {
        repository.deleteByUsernameAndTipo(username, tipo);

        byte[] bytes = new byte[32]; // 256 bits de aleatoriedade
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Duration validade = (tipo == TipoToken.LOGIN)
                ? Duration.ofMinutes(loginMinutos)
                : Duration.ofHours(registroHoras);
        Instant expira = Instant.now().plus(validade);

        EmailToken doc = new EmailToken();
        doc.setTokenHash(hash(token));
        doc.setUsername(username);
        doc.setTipo(tipo);
        doc.setExpiraEm(expira);
        doc.setApagarEm(expira.plus(FOLGA_LIMPEZA));
        repository.save(doc);
        return token;
    }

    /**
     * Consome o token (apaga) de forma ATÔMICA e devolve o username dono, se ele
     * existia,
     * era do tipo certo e não estava vencido. Segundo uso do mesmo token => vazio.
     */
    public Optional<String> consumir(String token, TipoToken tipo) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("tokenHash").is(hash(token)).and("tipo").is(tipo));
        EmailToken doc = mongoTemplate.findAndRemove(query, EmailToken.class);
        if (doc == null || doc.getExpiraEm().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(doc.getUsername());
    }

    /**
     * Existe link ainda válido para este usuário? (usado para liberar cadastros
     * pendentes vencidos)
     */
    public boolean temTokenValido(String username, TipoToken tipo) {
        return repository.existsByUsernameAndTipoAndExpiraEmAfter(username, tipo, Instant.now());
    }

    // Apaga os tokens do usuário para o tipo informado (invalida links pendentes).
    public void invalidar(String username, TipoToken tipo) {
        repository.deleteByUsernameAndTipo(username, tipo);
    }

    // Validade em minutos, para mostrar ao usuário.
    public long validadeMinutos(TipoToken tipo) {
        return tipo == TipoToken.LOGIN ? loginMinutos : registroHoras * 60;
    }

    // SHA-256 em hexadecimal: é o que se grava e consulta no banco, nunca o token
    // bruto.
    private static String hash(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
