// ============================================================
// JwtAuthFilter.java
// Autor: Miguel Santana
// Descrição: Lê JWT do header/cookie e autentica a requisição.
//            Ignora tokens preAuth (só valem para o puzzle).
// ============================================================
package com.pfc.thindesk.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pfc.thindesk.service.UsuarioService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Filtro que lê o JWT e popula o SecurityContext (ignora preAuth).
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String COOKIE_AUTH = "thindesk_auth";

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioService usuarioService;

    // Intercepta a requisição e autentica o usuário, se o token for final.
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extrairToken(request);

        if (token != null && jwtUtil.isTokenValido(token)) {
            Boolean isPreAuth = jwtUtil.extrairPreAuth(token);

            // preAuth não autentica: só serve para o fluxo do puzzle
            if (!Boolean.TRUE.equals(isPreAuth)) {
                String username = jwtUtil.extrairUsername(token);
                UserDetails userDetails = usuarioService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    // Tenta header primeiro, depois cookie
    // Obtém o token do header Authorization ou do cookie thindesk_auth.
    private String extrairToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_AUTH.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
