package com.pfc.thindesk.security.bot;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Primeiro filtro da aplicação: IP (ou navegador) bloqueado recebe 418 em QUALQUER rota. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BotBlockFilter extends OncePerRequestFilter {

    @Autowired
    private BotGuard guard;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        if (guard.estaBloqueado(req)) {
            guard.responderBule(resp);
            return;
        }
        chain.doFilter(req, resp);
    }
}
