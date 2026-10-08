package com.pfc.thindesk.controller;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;

/**
 * Controller de administração. Todas as rotas exigem ROLE_ADMIN.
 * O bloqueio é feito em dois níveis:
 * - SecurityConfig: /admin/** exige ROLE_ADMIN
 * - @PreAuthorize: defesa em profundidade (se a rota for movida por engano)
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = usuarioRepository.findAll();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "admin/usuarios :: content");
        return "admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/role")
    public String alterarRole(@PathVariable String id,
            @RequestParam String role) {
        usuarioRepository.findById(id).ifPresent(u -> {
            try {
                Role novo = Role.valueOf(role);
                u.setRoles(Set.of(novo));
                usuarioRepository.save(u);
            } catch (IllegalArgumentException ignored) {
            }
        });
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/toggle-ativo")
    public String alternarAtivo(@PathVariable String id) {
        usuarioRepository.findById(id).ifPresent(u -> {
            u.setAtivo(!u.isAtivo());
            usuarioRepository.save(u);
        });
        return "redirect:/admin/usuarios";
    }
}
