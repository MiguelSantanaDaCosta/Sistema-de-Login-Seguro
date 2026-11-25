package com.pfc.thindesk.controller;

import com.pfc.thindesk.entity.ItemCatalogo;
import com.pfc.thindesk.service.CatalogoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/{catalogo:tipo-chamado|recursos}")
public class CatalogoController {
    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping
    public String listar(@PathVariable String catalogo, Model model) {
        preparar(catalogo, model);
        model.addAttribute("itens", service.listar(catalogo));
        model.addAttribute("content", "catalogo/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    @PreAuthorize("hasRole('ADMIN')")
    public String novo(@PathVariable String catalogo, Model model) {
        model.addAttribute("item", new ItemCatalogo());
        return formulario(catalogo, null, model);
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editar(@PathVariable String catalogo, @PathVariable String id, Model model) {
        model.addAttribute("item", service.buscar(catalogo, id));
        return formulario(catalogo, id, model);
    }

    @PostMapping("/novo")
    @PreAuthorize("hasRole('ADMIN')")
    public String criar(@PathVariable String catalogo, @Valid @ModelAttribute("item") ItemCatalogo item,
                        BindingResult erros, Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) return formulario(catalogo, null, model);
        service.salvar(catalogo, null, item);
        redirect.addFlashAttribute("sucesso", "Item cadastrado.");
        return "redirect:/" + catalogo;
    }

    @PostMapping("/editar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String salvar(@PathVariable String catalogo, @PathVariable String id,
                         @Valid @ModelAttribute("item") ItemCatalogo item, BindingResult erros,
                         Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) return formulario(catalogo, id, model);
        service.salvar(catalogo, id, item);
        redirect.addFlashAttribute("sucesso", "Item atualizado.");
        return "redirect:/" + catalogo;
    }

    @PostMapping("/excluir/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String excluir(@PathVariable String catalogo, @PathVariable String id, RedirectAttributes redirect) {
        service.excluir(catalogo, id);
        redirect.addFlashAttribute("sucesso", "Item excluído.");
        return "redirect:/" + catalogo;
    }

    private String formulario(String catalogo, String id, Model model) {
        preparar(catalogo, model);
        model.addAttribute("acao", "/" + catalogo + (id == null ? "/novo" : "/editar/" + id));
        model.addAttribute("content", "catalogo/formulario :: content");
        return "layout";
    }

    private void preparar(String catalogo, Model model) {
        model.addAttribute("catalogo", catalogo);
        model.addAttribute("tituloCatalogo", "tipo-chamado".equals(catalogo) ? "Tipos de chamado" : "Recursos");
        model.addAttribute("descricaoCatalogo", "tipo-chamado".equals(catalogo)
                ? "Categorias disponíveis ao abrir um chamado. Administradores gerenciam o catálogo."
                : "Equipamentos, ferramentas e serviços disponíveis para atendimento. Administradores gerenciam o catálogo.");
    }
}
