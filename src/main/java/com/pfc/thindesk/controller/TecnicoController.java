package com.pfc.thindesk.controller;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.service.ChamadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Área do técnico. Exige ROLE_TECNICO ou ROLE_ADMIN.
 * Técnicos podem assumir chamados e movê-los entre estados.
 */
@Controller
@RequestMapping("/tecnico")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
public class TecnicoController {

    @Autowired
    private ChamadoService chamadoService;

    @GetMapping("/chamados")
    public String listarChamados(Model model, Authentication auth) {
        List<Chamado> chamados = chamadoService.listarChamados();
        model.addAttribute("chamados", chamados);
        model.addAttribute("usuarioAtual", auth.getName());
        model.addAttribute("content", "tecnico/chamados :: content");
        return "tecnico/chamados";
    }

    @PostMapping("/chamados/{id}/assumir")
    public String assumir(@PathVariable String id, Authentication auth) {
        chamadoService.atualizarChamado(id, montarAtualizacao("Em Andamento", auth.getName()));
        return "redirect:/tecnico/chamados";
    }

    @PostMapping("/chamados/{id}/status")
    public String alterarStatus(@PathVariable String id,
                                @RequestParam String status,
                                Authentication auth) {
        chamadoService.atualizarChamado(id, montarAtualizacao(status, auth.getName()));
        return "redirect:/tecnico/chamados";
    }

    // TODO: ChamadoService.atualizarChamado hoje sobrescreve todos os campos; avaliar patch parcial.
    private Chamado montarAtualizacao(String status, String tecnico) {
        Chamado c = new Chamado();
        c.setStatus(status);
        c.setTecnico(tecnico);
        return c;
    }
}
