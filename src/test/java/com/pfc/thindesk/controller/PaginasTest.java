package com.pfc.thindesk.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import com.pfc.SecurityConfig;
import com.pfc.thindesk.entity.ItemCatalogo;
import com.pfc.thindesk.repository.UsuarioRepository;
import com.pfc.thindesk.root.RootProps;
import com.pfc.thindesk.security.JwtUtil;
import com.pfc.thindesk.security.bot.BotGuard;
import com.pfc.thindesk.service.*;

/** Renderiza os templates reais para detectar links sem rota e modelos incompletos. */
@WebMvcTest({HomeController.class, CatalogoController.class, ClientePaginaController.class,
        HorarioAtendimentoController.class, EquipeController.class})
@Import(SecurityConfig.class)
class PaginasTest {
    @Autowired MockMvc mvc;
    @MockBean ClienteService clientes;
    @MockBean HorarioAtendimentoService horarios;
    @MockBean CatalogoService catalogos;
    @MockBean UsuarioRepository usuarios;
    @MockBean UsuarioService usuarioService;
    @MockBean ThemeService temas;
    @MockBean JwtUtil jwt;
    @MockBean BotGuard guard;
    @MockBean(name = "rootProps") RootProps rootProps;

    @ParameterizedTest
    @ValueSource(strings = {"/clientes", "/tecnicos", "/tipo-chamado", "/recursos", "/ajustes-horarios",
            "/clientes/novo", "/tipo-chamado/novo", "/recursos/novo"})
    void paginasRenderizam(String caminho) throws Exception {
        mvc.perform(get(caminho).with(user("admin").roles("ADMIN", "TECNICO", "USUARIO")))
                .andExpect(status().isOk()).andExpect(view().name("layout"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thindesk")));
    }

    @Test
    void visitanteVaiParaLoginEApiRecebe401() throws Exception {
        mvc.perform(get("/clientes")).andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?sessao=expirada"));
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/clientes/novo", "/tipo-chamado/novo", "/recursos/novo"})
    void usuarioNaoGerenciaCadastros(String caminho) throws Exception {
        mvc.perform(get(caminho).with(user("demo").roles("USUARIO"))).andExpect(status().isForbidden());
    }

    @Test
    void horarioInvalidoReexibeFormularioSemSalvar() throws Exception {
        mvc.perform(post("/ajustes-horarios/salvar").with(user("admin").roles("ADMIN"))
                        .param("setor", "TI").param("diaSemana", "Segunda-feira")
                        .param("horarioInicio", "18:00").param("horarioFim", "08:00"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("novoHorario", "horarioFim"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("O fim deve ser posterior")));
        verify(horarios, never()).salvar(any());
    }

    @Test
    void horarioValidoSalvaERedireciona() throws Exception {
        mvc.perform(post("/ajustes-horarios/salvar").with(user("admin").roles("ADMIN"))
                        .param("setor", "TI").param("diaSemana", "Segunda-feira")
                        .param("horarioInicio", "08:00").param("horarioFim", "18:00"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/ajustes-horarios"));
        verify(horarios).salvar(any());
    }

    @Test
    void clienteInvalidoReexibeFormulario() throws Exception {
        mvc.perform(post("/clientes/novo").with(user("tecnico").roles("TECNICO"))
                        .param("nome", " ").param("setor", "TI"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("cliente", "nome"));
        verify(clientes, never()).novoCliente(any());
    }

    @Test
    void catalogoRenderizaDadosEscapados() throws Exception {
        ItemCatalogo item = new ItemCatalogo();
        item.setId("id");
        item.setNome("<script>alert(1)</script>");
        item.setDescricao("Equipamento");
        when(catalogos.listar("recursos")).thenReturn(List.of(item));
        mvc.perform(get("/recursos").with(user("demo").roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/recursos/excluir/"))));
    }
}
