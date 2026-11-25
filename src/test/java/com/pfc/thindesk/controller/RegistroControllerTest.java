package com.pfc.thindesk.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pfc.thindesk.dto.RegistroRequest;
import com.pfc.thindesk.service.EmailService;
import com.pfc.thindesk.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistroControllerTest {
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        var controller = new RegistroController();
        ReflectionTestUtils.setField(controller, "usuarioService", usuarios);
        ReflectionTestUtils.setField(controller, "emailService", mock(EmailService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void normalizaAntesDeValidarCadastro() throws Exception {
        when(usuarios.registrar(any())).thenReturn(new UsuarioService.ResultadoRegistro("token", true));
        mvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"  João Silva  ","email":"  JOAO@example.com  ","nomeCompleto":" João Silva ","password":"segredo123"}
                """)).andExpect(status().isCreated());
        var req = ArgumentCaptor.forClass(RegistroRequest.class);
        verify(usuarios).registrar(req.capture());
        assertEquals("João Silva", req.getValue().username);
        assertEquals("joao@example.com", req.getValue().email);
    }

    @Test
    void senhaUnicodeAcimaDoLimiteBcryptNaoViraErro500() throws Exception {
        mvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"joao\",\"nomeCompleto\":\"João\",\"email\":\"joao@example.com\",\"password\":\""
                                + "á".repeat(37) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value(com.pfc.thindesk.util.Senha.REGRA));
        verifyNoInteractions(usuarios);
    }
}
