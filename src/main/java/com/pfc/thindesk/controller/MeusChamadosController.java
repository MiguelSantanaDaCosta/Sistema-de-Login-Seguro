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
    @Autowired
    private com.pfc.thindesk.service.CatalogoService catalogoService;

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
        model.addAttribute("tipos", catalogoService.listar("tipo-chamado"));
        model.addAttribute("content", "chamados/novo :: content");
        return "layout";
    }

    @PostMapping("/novo")
    public String criar(@RequestParam String titulo, @RequestParam String descricao,
                        @RequestParam(defaultValue = "") String tipoId,
                        Model model, RedirectAttributes redirect) {
        try {
            String texto = descricao;
            if (!tipoId.isBlank()) {
                String tipo = catalogoService.listar("tipo-chamado").stream().filter(i -> tipoId.equals(i.getId()))
                        .map(com.pfc.thindesk.entity.ItemCatalogo::getNome).findFirst()
                        .orElseThrow(() -> new ChamadoApiException(400, "Tipo indisponível. Selecione outro tipo."));
                texto = "Tipo: " + tipo + "\n\n" + descricao;
            }
            if (titulo.isBlank() || texto.isBlank() || titulo.length() > 120 || texto.length() > 2000) {
                throw new ChamadoApiException(400, "Informe título (até 120 caracteres) e descrição (até 2000, incluindo o tipo).");
            }
            chamadoClient.abrir(titulo.trim(), texto.trim());
            redirect.addFlashAttribute("sucesso", "Chamado aberto com sucesso.");
            return "redirect:/chamados";
        } catch (ChamadoApiException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("titulo", titulo);
            model.addAttribute("descricao", descricao);
            model.addAttribute("tipoId", tipoId);
            return formulario(model);
        }
    }
}
