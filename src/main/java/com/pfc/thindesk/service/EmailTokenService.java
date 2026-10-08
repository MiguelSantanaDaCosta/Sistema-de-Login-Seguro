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

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private EmailTokenRepository repository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${app.security.email-token.login-minutos:15}")
    private long loginMinutos;
    @Value("${app.security.email-token.registro-horas:24}")
    private long registroHoras;

    /**
     * Gera um token novo e devolve o valor BRUTO (vai só no email). Tokens
     * anteriores do
     * mesmo usuário e tipo são apagados: apenas o último link enviado vale.
     */
    public String gerar(String username, TipoToken tipo) {
        repository.deleteByUsernameAndTipo(username, tipo);

        byte[] bytes = new byte[32]; // 256 bits de aleatoriedade
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Duration validade = (tipo == TipoToken.LOGIN)
                ? Duration.ofMinutes(loginMinutos)
                : Duration.ofHours(registroHoras);

        EmailToken doc = new EmailToken();
        doc.setTokenHash(hash(token));
        doc.setUsername(username);
        doc.setTipo(tipo);
        doc.setExpiraEm(Instant.now().plus(validade));
        repository.save(doc);
        return token;
    }

    // Consome o token apaga e devolve o username
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

    public boolean temTokenValido(String username, TipoToken tipo) {
        return repository.existsByUsernameAndTipoAndExpiraEmAfter(username, tipo, Instant.now());
    }

    public void invalidar(String username, TipoToken tipo) {
        repository.deleteByUsernameAndTipo(username, tipo);
    }

    // Validade em minutos
    public long validadeMinutos(TipoToken tipo) {
        return tipo == TipoToken.LOGIN ? loginMinutos : registroHoras * 60;
    }

    private static String hash(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
