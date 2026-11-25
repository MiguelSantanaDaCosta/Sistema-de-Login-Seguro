package com.pfc.thindesk.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pfc.thindesk.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtAuthFilterTest {
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter();

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "usuarioService", usuarios);
        when(jwt.isTokenValido("token")).thenReturn(true);
        when(jwt.extrairPreAuth("token")).thenReturn(false);
        when(jwt.extrairUsername("token")).thenReturn("joao");
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse executar() throws Exception {
        var request = new MockHttpServletRequest("GET", "/login");
        request.setCookies(new Cookie("thindesk_auth", "token"));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertNotNull(chain.getRequest(), "Cookie antigo não pode interromper a página de login");
        return response;
    }

    @Test
    void contaRemovidaNaoDerrubaLogin() throws Exception {
        when(usuarios.loadUserByUsername("joao")).thenThrow(new UsernameNotFoundException("removido"));
        assertTrue(executar().getHeader("Set-Cookie").contains("Max-Age=0"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void contaDesativadaNaoMantemSessao() throws Exception {
        when(usuarios.loadUserByUsername("joao"))
                .thenReturn(User.withUsername("joao").password("hash").roles("USUARIO").disabled(true).build());
        assertTrue(executar().getHeader("Set-Cookie").contains("Max-Age=0"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenExpiradoEhDescartado() throws Exception {
        when(jwt.isTokenValido("token")).thenReturn(false);
        assertTrue(executar().getHeader("Set-Cookie").contains("Max-Age=0"));
        verifyNoInteractions(usuarios);
    }

    @Test
    void preAuthNaoAutenticaPaginas() throws Exception {
        when(jwt.extrairPreAuth("token")).thenReturn(true);
        executar();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(usuarios);
    }
}
