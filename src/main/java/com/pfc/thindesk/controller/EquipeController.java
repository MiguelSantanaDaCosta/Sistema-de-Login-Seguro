package com.pfc.thindesk.controller;

import java.util.Comparator;
import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EquipeController {
    private final UsuarioRepository usuarios;

    public EquipeController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/tecnicos")
    public String tecnicos(Model model) {
        model.addAttribute("tecnicos", usuarios.findAll().stream()
                .filter(Usuario::isAtivo)
                .filter(u -> Role.expandir(u.getRoles()).contains(Role.ROLE_TECNICO))
                .filter(u -> !u.getRoles().contains(Role.ROLE_ROOT))
                .sorted(Comparator.comparing(Usuario::getUsername))
                .map(u -> new Tecnico(u.getUsername(), u.getNomeCompleto(),
                        u.getRoles().contains(Role.ROLE_ADMIN) ? "Administrador" : "Técnico"))
                .toList());
        model.addAttribute("content", "tecnicos :: content");
        return "layout";
    }

    public record Tecnico(String username, String nome, String perfil) {
    }
}
