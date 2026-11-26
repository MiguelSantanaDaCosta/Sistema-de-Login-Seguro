package com.pfc.thindesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envia os emails de confirmação (login e cadastro). Com app.mail.enabled=false
 * (ou sem
 * SMTP), o link é escrito no log do servidor — útil em desenvolvimento.
 */
@Service
public class EmailService {

    // Logger usado no modo sem SMTP (o link vai para o log) e nos erros de envio.
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Cliente SMTP do Spring; pode ser nulo se não houver configuração de email.
    @Autowired(required = false)
    private JavaMailSender mailSender;

    // Liga o envio real; com false, os links são apenas escritos no log do
    // servidor.
    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    // Endereço público da aplicação, base dos links enviados por email.
    @Value("${app.base-url:http://localhost:8000}")
    private String baseUrl;

    // Validade do link de login (usada só no texto do email).
    @Value("${app.security.email-token.login-minutos:15}")
    private long loginMinutos;

    // Validade do link de redefinição de senha (usada só no texto do email).
    @Value("${app.security.email-token.redefinicao-minutos:30}")
    private long redefinicaoMinutos;

    // Remetente (o usuário SMTP). Sem isso o provedor escolhe o endereço sozinho.
    @Value("${spring.mail.username:}")
    private String remetente;

    // Validade do link de cadastro (usada só no texto do email).
    @Value("${app.security.email-token.registro-horas:24}")
    private long registroHoras;

    // Monta a URL de confirmação de login para o token informado.
    public String linkLogin(String token) {
        return baseUrl + "/auth/confirmar?token=" + token;
    }

    // Monta a URL de redefinição de senha para o token informado.
    public String linkRedefinicao(String token) {
        return baseUrl + "/redefinir-senha?token=" + token;
    }

    // Monta a URL de confirmação de cadastro para o token informado.
    public String linkRegistro(String token) {
        return baseUrl + "/auth/confirmar-registro?token=" + token;
    }

    // Envia o link de login depois que o usuário resolve o puzzle.
    public boolean enviarTokenConfirmacao(String destinatario, String token, String nomeCompleto) {
        String link = linkLogin(token);
        String corpo = "Olá " + nomeCompleto + "!\n\n"
                + "Você resolveu o puzzle!\n\n"
                + "Clique no link abaixo para concluir o login:\n\n" + link + "\n\n"
                + "O link expira em " + loginMinutos + " minutos e só pode ser usado uma vez.\n"
                + "Se não foi você, ignore este email e troque a sua senha.";
        return enviar(destinatario, "Thindesk — Confirmação de login", corpo, link);
    }

    // Envia o link que confirma o email e ativa a conta recém-cadastrada.
    public boolean enviarTokenRegistro(String destinatario, String token, String nomeCompleto) {
        String link = linkRegistro(token);
        String corpo = "Bem-vindo ao Thindesk, " + nomeCompleto + "!\n\n"
                + "Confirme o seu email para ativar a conta:\n\n" + link + "\n\n"
                + "O link expira em " + registroHoras + " horas e só pode ser usado uma vez.\n"
                + "Se você não criou esta conta, ignore este email.";
        return enviar(destinatario, "Thindesk — Confirme o seu cadastro", corpo, link);
    }

    // Envia o link para criar uma nova senha (esqueci minha senha).
    public boolean enviarTokenRedefinicao(String destinatario, String token, String nomeCompleto) {
        String link = linkRedefinicao(token);
        String corpo = "Olá " + nomeCompleto + "!\n\n"
                + "Recebemos um pedido para redefinir a senha da sua conta no Thindesk.\n\n"
                + "Clique no link abaixo para escolher uma nova senha:\n\n" + link + "\n\n"
                + "O link expira em " + redefinicaoMinutos + " minutos e só pode ser usado uma vez.\n"
                + "Se não foi você, ignore este email: a sua senha continua a mesma.";
        return enviar(destinatario, "Thindesk — Redefinição de senha", corpo, link);
    }

    // Avisa que a senha foi alterada (para o dono perceber se não foi ele).
    public boolean enviarSenhaAlterada(String destinatario, String nomeCompleto) {
        String corpo = "Olá " + nomeCompleto + "!\n\n"
                + "A senha da sua conta no Thindesk acabou de ser alterada.\n\n"
                + "Se foi você, não precisa fazer nada. Se não foi, peça uma nova redefinição de senha "
                + "imediatamente e avise o administrador.";
        return enviar(destinatario, "Thindesk — Sua senha foi alterada", corpo, baseUrl + "/esqueci-senha");
    }

    // Envia o email e devolve se deu certo. Sem SMTP (modo desenvolvimento) o link vai para o log e conta como
    // enviado; se o SMTP falhar devolve false (o link vai para o log) para o chamador avisar o usuário, em vez de
    // fingir que o email saiu.
    private boolean enviar(String destinatario, String assunto, String corpo, String link) {
        if (!mailEnabled || mailSender == null) {
            log.warn("=== MAIL DESABILITADO — {} ===", assunto);
            log.warn("Para: {}", destinatario);
            log.warn("Link: {}", link);
            log.warn("================================");
            return true;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (remetente != null && remetente.contains("@")) {
                msg.setFrom("Thindesk <" + remetente + ">");
            }
            msg.setTo(destinatario);
            msg.setSubject(assunto);
            msg.setText(corpo);
            mailSender.send(msg);
            log.info("Email '{}' enviado para {}", assunto, destinatario);
            return true;
        } catch (Exception e) {
            log.error("Falha ao enviar email para {}: {}", destinatario, e.getMessage());
            log.warn("=== FALLBACK — {} ===", assunto);
            log.warn("Link: {}", link);
            return false;
        }
    }
}
