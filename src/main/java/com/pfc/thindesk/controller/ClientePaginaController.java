package com.pfc.thindesk.controller;

import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
@PreAuthorize("hasRole('TECNICO')")
public class ClientePaginaController {
    private final ClienteService service;

    public ClientePaginaController(ClienteService service) {
        this.service = service;
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("cliente", new Cliente());
        return formulario(model, null);
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        model.addAttribute("cliente", service.buscar(id));
        return formulario(model, id);
    }

    @PostMapping("/novo")
    public String criar(@Valid @ModelAttribute("cliente") Cliente cliente, BindingResult erros,
                        Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) return formulario(model, null);
        cliente.setId(null);
        service.novoCliente(cliente);
        redirect.addFlashAttribute("sucesso", "Cliente cadastrado.");
        return "redirect:/clientes";
    }

    @PostMapping("/editar/{id}")
    public String salvar(@PathVariable String id, @Valid @ModelAttribute("cliente") Cliente cliente,
                         BindingResult erros, Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) return formulario(model, id);
        service.atualizar(id, cliente);
        redirect.addFlashAttribute("sucesso", "Cliente atualizado.");
        return "redirect:/clientes";
    }

    @PostMapping("/deletar/{id}")
    public String excluir(@PathVariable String id, RedirectAttributes redirect) {
        service.excluir(id);
        redirect.addFlashAttribute("sucesso", "Cliente excluído.");
        return "redirect:/clientes";
    }

    private String formulario(Model model, String id) {
        model.addAttribute("acao", id == null ? "/clientes/novo" : "/clientes/editar/" + id);
        model.addAttribute("content", "clientes/formulario :: content");
        return "layout";
    }
}
