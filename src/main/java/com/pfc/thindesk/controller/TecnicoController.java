package com.pfc.thindesk.controller;

import java.util.List;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pfc.thindesk.client.ChamadoApiException;
import com.pfc.thindesk.client.ChamadoClient;

/**
 * Área do técnico. Exige ROLE_TECNICO ou ROLE_ADMIN.
 * Lista os chamados abertos e registra a solução no ms-chamados.
 */
@Controller
@RequestMapping("/tecnico")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
public class TecnicoController {

    @Autowired
    private ChamadoClient chamadoClient;

    @GetMapping("/chamados")
    public String fila(Model model, Authentication auth) {
        try {
            model.addAttribute("chamados", chamadoClient.listarAbertos());
        } catch (ChamadoApiException e) {
            model.addAttribute("chamados", List.of());
            model.addAttribute("erro", e.getMessage());
        }
        model.addAttribute("usuarioAtual", auth.getName());
        model.addAttribute("content", "tecnico/chamados :: content");
        return "layout";
    }

    @PostMapping("/chamados/{id}/resolver")
    public String resolver(@PathVariable String id,
                           @RequestParam String solucao,
                           RedirectAttributes redirect) {
        try {
            chamadoClient.resolver(id, solucao);
            redirect.addFlashAttribute("sucesso", "Chamado resolvido.");
        } catch (ChamadoApiException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/tecnico/chamados";
    }
}
