package com.pfc.thindesk.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pfc.thindesk.client.ChamadoApiException;
import com.pfc.thindesk.client.ChamadoClient;

/** Telas do usuário: lista os próprios chamados e abre novos no ms-chamados. */
@Controller
@RequestMapping("/chamados")
public class MeusChamadosController {

    @Autowired
    private ChamadoClient chamadoClient;

    @GetMapping
    public String lista(Model model, Authentication auth) {
        try {
            model.addAttribute("chamados", chamadoClient.listarDoUsuario(auth.getName()));
        } catch (ChamadoApiException e) {
            model.addAttribute("chamados", List.of());
            model.addAttribute("erro", e.getMessage());
        }
        model.addAttribute("content", "chamados :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String formulario(Model model) {
        model.addAttribute("content", "chamados/novo :: content");
        return "layout";
    }

    @PostMapping("/novo")
    public String criar(@RequestParam String titulo, @RequestParam String descricao,
                        Model model, RedirectAttributes redirect) {
        try {
            chamadoClient.abrir(titulo, descricao);
            redirect.addFlashAttribute("sucesso", "Chamado aberto com sucesso.");
            return "redirect:/chamados";
        } catch (ChamadoApiException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("titulo", titulo);
            model.addAttribute("descricao", descricao);
            model.addAttribute("content", "chamados/novo :: content");
            return "layout";
        }
    }
}
