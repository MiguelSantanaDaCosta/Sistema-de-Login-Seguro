package com.pfc.thindesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.base-url:http://localhost:8000}")
    private String baseUrl;

    public void enviarTokenConfirmacao(String destinatario, String token) {
        String link = baseUrl + "/auth/confirmar?token=" + token;
        String assunto = "Thindesk — Confirmação de login";
        String corpo = "Você resolveu o puzzle!\n\n" +
                       "Clique no link abaixo para concluir o login:\n\n" +
                       link + "\n\n" +
                       "Este link expira em 1 hora.";

        if (!mailEnabled || mailSender == null) {
            log.warn("=== MAIL DESABILITADO — link de confirmação ===");
            log.warn("Para: {}", destinatario);
            log.warn("Link: {}", link);
            log.warn("===============================================");
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(destinatario);
            msg.setSubject(assunto);
            msg.setText(corpo);
            mailSender.send(msg);
            log.info("Email de confirmação enviado para {}", destinatario);
        } catch (Exception e) {
            log.error("Falha ao enviar email para {}: {}", destinatario, e.getMessage());
            log.warn("=== FALLBACK — link de confirmação ===");
            log.warn("Link: {}", link);
        }
    }
}
