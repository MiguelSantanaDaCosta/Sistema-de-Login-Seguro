package com.pfc.thindesk.security.bot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Proteção contra bots/IAs no desafio root.
 *
 * Sinais (qualquer um => bloqueio imediato do IP por 1h, resposta 418 "Eu sou um bule de chá"):
 *  - User-Agent vazio ou de bot/automação/crawler de IA; ausência de Accept-Language
 *  - cliente sem o cookie assinado de visita (não carregou a página: chamou a API direto)
 *  - campo-isca (honeypot) preenchido ou navigator.webdriver verdadeiro
 *  - rajada de inícios de desafio; resposta mais rápida que humanamente possível (ver RootDesafioService)
 *
 * O IP vem da conexão (getRemoteAddr), nunca de header enviado pelo cliente. Os cookies servem para
 * (1) provar que o cliente passou pela página e (2) manter o bloqueio no navegador mesmo se o IP mudar.
 * Heurística: barra automação óbvia; um navegador real dirigido por humano/IA não é detectável com certeza.
 */
@Component
public class BotGuard {

    private static final Logger log = LoggerFactory.getLogger(BotGuard.class);

    public static final String COOKIE_VISITA = "thindesk_visita";
    public static final String COOKIE_BULE = "thindesk_bule";
    public static final String TEXTO_BULE = "Eu sou um bule de chá";

    private static final Pattern UA_BOT = Pattern.compile(
            "(?i)(\\bbot\\b|[a-z]bot\\b|bot[/;)]|crawl|spider|slurp|scrap|curl|wget|httpie|httpclient|http-client"
                    + "|okhttp|python|aiohttp|urllib|requests|java/|go-http|libwww|axios|node-fetch|undici|postman"
                    + "|insomnia|headless|phantom|selenium|webdriver|puppeteer|playwright|chatgpt|gptbot|openai"
                    + "|claude|anthropic|perplexity|bytespider|ccbot|cohere|gemini|bard|copilot|mistral|llama"
                    + "|dataforseo|ahrefs|semrush)");

    // cache de consultas ao banco: ip -> (bloqueado?, verificado em, ate)
    private record Estado(boolean bloqueado, long verificadoEm, long ateMs) {
    }

    private final ConcurrentHashMap<String, Estado> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> inicios = new ConcurrentHashMap<>();

    @Autowired
    private IpBloqueadoRepository repo;

    @Value("${app.jwt.secret}")
    private String segredo;

    @Value("${app.bot.protecao:true}")
    private boolean ativa;

    @Value("${app.bot.bloqueio-minutos:60}")
    private long bloqueioMinutos;

    @Value("${app.bot.ips-permitidos:}")
    private String ipsPermitidosCsv;

    @Value("${app.bot.max-inicios-por-5min:8}")
    private int maxInicios;

    private static final long REVERIFICAR_MS = 30_000;

    // ---------------- IP / bloqueio ----------------

    public String ip(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    private Set<String> permitidos() {
        return Arrays.stream(ipsPermitidosCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** IP bloqueado (banco, com cache de 30s) ou navegador com cookie de bloqueio válido. */
    public boolean estaBloqueado(HttpServletRequest req) {
        if (!ativa) {
            return false;
        }
        String ip = ip(req);
        if (permitidos().contains(ip)) {
            return false;
        }
        long agora = System.currentTimeMillis();
        if (cookieBuleValido(req, agora)) {
            return true;
        }
        Estado e = cache.get(ip);
        if (e != null && agora - e.verificadoEm() < REVERIFICAR_MS) {
            return e.bloqueado() && agora < e.ateMs();
        }
        var opt = repo.findById(ip);
        boolean bloq = opt.isPresent() && opt.get().getAte().toEpochMilli() > agora;
        cache.put(ip, new Estado(bloq, agora, bloq ? opt.get().getAte().toEpochMilli() : 0));
        return bloq;
    }

    /** Registra o bloqueio (1h), marca o navegador com cookie e responde 418. */
    public void bloquear(HttpServletRequest req, HttpServletResponse resp, String motivo) {
        String ip = ip(req);
        if (!permitidos().contains(ip)) {
            Instant ate = Instant.now().plus(Duration.ofMinutes(bloqueioMinutos));
            IpBloqueado b = new IpBloqueado();
            b.setIp(ip);
            b.setAte(ate);
            b.setMotivo(motivo);
            String ua = req.getHeader("User-Agent");
            b.setUserAgent(ua == null ? null : ua.substring(0, Math.min(ua.length(), 200)));
            repo.save(b);
            cache.put(ip, new Estado(true, System.currentTimeMillis(), ate.toEpochMilli()));
            resp.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_BULE, assinar("bule|" + ate.toEpochMilli(), ate.toEpochMilli()))
                    .httpOnly(true).path("/").sameSite("Lax").maxAge(Duration.ofMinutes(bloqueioMinutos)).build().toString());
            log.warn("BOT bloqueado: ip={} por {} min — motivo: {}", ip, bloqueioMinutos, motivo);
        }
        responderBule(resp);
    }

    public void responderBule(HttpServletResponse resp) {
        try {
            resp.setStatus(418);
            resp.setContentType("text/html;charset=UTF-8");
            resp.setHeader("Retry-After", String.valueOf(bloqueioMinutos * 60));
            resp.getWriter().write("<!doctype html><meta charset=\"utf-8\"><title>418</title>"
                    + "<body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\">"
                    + "<h1>" + TEXTO_BULE + "</h1><p style=\"font-size:4rem\">&#9749;</p></body>");
            resp.flushBuffer();
        } catch (java.io.IOException ignored) {
            // cliente já foi embora
        }
    }

    // ---------------- Detecção ----------------

    /** Motivo se o cliente parece automação; null se parece navegador. */
    public String avaliarCliente(HttpServletRequest req) {
        if (!ativa) {
            return null;
        }
        String ua = req.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return "User-Agent ausente";
        }
        if (UA_BOT.matcher(ua).find()) {
            return "User-Agent de bot/automação/IA: " + ua.substring(0, Math.min(ua.length(), 80));
        }
        if (req.getHeader("Accept-Language") == null) {
            return "sem Accept-Language (cliente não-navegador)";
        }
        return null;
    }

    /** Chamadas à API do desafio: exige o cookie de visita, honeypot vazio e sem webdriver. */
    public String avaliarChamadaApi(HttpServletRequest req, String honeypot, String webdriver) {
        if (!ativa) {
            return null;
        }
        String m = avaliarCliente(req);
        if (m != null) {
            return m;
        }
        if (!visitaValida(req)) {
            return "sem cookie de visita válido (não passou pela página do desafio)";
        }
        if (honeypot != null && !honeypot.isBlank()) {
            return "campo-isca preenchido";
        }
        if ("1".equals(webdriver) || "true".equalsIgnoreCase(webdriver)) {
            return "navigator.webdriver ativo (automação)";
        }
        return null;
    }

    /** Rajada de inícios de desafio a partir do mesmo IP. */
    public boolean rajadaDeInicios(HttpServletRequest req) {
        if (!ativa) {
            return false;
        }
        long agora = System.currentTimeMillis();
        Deque<Long> fila = inicios.computeIfAbsent(ip(req), k -> new ArrayDeque<>());
        synchronized (fila) {
            fila.addLast(agora);
            while (!fila.isEmpty() && agora - fila.peekFirst() > 300_000) {
                fila.pollFirst();
            }
            return fila.size() > maxInicios;
        }
    }

    // ---------------- Cookie de visita (assinado, preso ao IP) ----------------

    public void emitirCookieVisita(HttpServletRequest req, HttpServletResponse resp) {
        long ts = System.currentTimeMillis();
        String valor = ts + "." + hmac("visita|" + ts + "|" + ip(req));
        resp.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_VISITA, valor)
                .httpOnly(true).path("/").sameSite("Lax").maxAge(Duration.ofMinutes(30)).build().toString());
    }

    private boolean visitaValida(HttpServletRequest req) {
        String v = cookie(req, COOKIE_VISITA);
        if (v == null || !v.contains(".")) {
            return false;
        }
        try {
            String[] p = v.split("\\.", 2);
            long ts = Long.parseLong(p[0]);
            return System.currentTimeMillis() - ts < 30 * 60_000L
                    && igual(p[1], hmac("visita|" + ts + "|" + ip(req)));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean cookieBuleValido(HttpServletRequest req, long agora) {
        String v = cookie(req, COOKIE_BULE);
        if (v == null || !v.contains(".")) {
            return false;
        }
        try {
            String[] p = v.split("\\.", 2);
            long ate = Long.parseLong(p[0]);
            return ate > agora && igual(p[1], hmac("bule|" + ate));
        } catch (RuntimeException e) {
            return false;
        }
    }

    // valor do cookie de bloqueio = "<ate>.<hmac>"
    private String assinar(String dado, long ate) {
        return ate + "." + hmac(dado);
    }

    private String cookie(HttpServletRequest req, String nome) {
        Cookie[] cs = req.getCookies();
        if (cs != null) {
            for (Cookie c : cs) {
                if (nome.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private String hmac(String dado) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(dado.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean igual(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
