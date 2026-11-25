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
        return "layout";
    }

    @PostMapping("/usuarios/{id}/role")
    public String alterarRole(@PathVariable String id,
            @RequestParam String role) {
        usuarioRepository.findById(id).ifPresent(u -> {
            try {
                Role novo = Role.valueOf(role);
                if (novo == Role.ROLE_ROOT || u.getRoles().contains(Role.ROLE_ROOT)) {
                    return; // ROOT só se obtém pelo desafio; nunca é atribuído/removido aqui
                }
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

    // ------------------------------------------------------------------
    // Contas de demonstração: criadas, redefinidas e removidas por um ADMIN (nada fixo no código)
    // ------------------------------------------------------------------

    private static final java.util.Set<Role> ROLES_DEMO = java.util.EnumSet.of(Role.ROLE_USUARIO, Role.ROLE_TECNICO, Role.ROLE_ADMIN);

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired
    private com.pfc.thindesk.service.DemoProps demoProps;

    @GetMapping("/demos")
    public String demos(Model model) {
        model.addAttribute("demos", usuarioRepository.findAll().stream()
                .filter(u -> u.isContaDemo() || u.isPulaEmail()).toList());
        model.addAttribute("demoHabilitado", demoProps.isHabilitado());
        model.addAttribute("perfis", ROLES_DEMO);
        model.addAttribute("content", "admin/demos :: content");
        return "layout";
    }

    /** modo: SENHA (entra só com senha) ou PUZZLE (resolve o puzzle do perfil, sem link por email). */
    @PostMapping("/demos")
    public String criarDemo(@RequestParam String username, @RequestParam String password,
            @RequestParam String role, @RequestParam String modo,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        String user = username == null ? "" : username.trim().toLowerCase();
        try {
            Role perfil = Role.valueOf(role);
            if (!ROLES_DEMO.contains(perfil)) {
                throw new IllegalArgumentException("Perfil não permitido para conta demo (ROOT só se obtém pelo desafio).");
            }
            if (!user.matches("[a-z0-9._-]{3,30}")) {
                throw new IllegalArgumentException("Usuário: 3 a 30 caracteres (letras minúsculas, números, ponto, hífen ou _).");
            }
            if (password == null || password.length() < 6) {
                throw new IllegalArgumentException("A senha precisa ter ao menos 6 caracteres.");
            }
            if (!"SENHA".equals(modo) && !"PUZZLE".equals(modo)) {
                throw new IllegalArgumentException("Modo inválido.");
            }
            String email = user + "@demo.thindesk.local";
            if (usuarioRepository.existsByUsername(user) || usuarioRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Já existe um usuário '" + user + "'.");
            }
            Usuario u = new Usuario();
            u.setUsername(user);
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode(password));
            u.setNomeCompleto("Demonstração (" + perfil.name().substring(5).toLowerCase() + ")");
            u.setAtivo(true);
            u.setRoles(Set.of(perfil));
            u.setContaDemo("SENHA".equals(modo));
            u.setPulaEmail("PUZZLE".equals(modo));
            usuarioRepository.save(u);
            redirect.addFlashAttribute("sucesso", "Conta demo '" + user + "' criada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/demos";
    }

    @PostMapping("/demos/{id}/senha")
    public String redefinirSenhaDemo(@PathVariable String id, @RequestParam String password,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        usuarioRepository.findById(id).filter(u -> u.isContaDemo() || u.isPulaEmail()).ifPresent(u -> {
            if (password == null || password.length() < 6) {
                redirect.addFlashAttribute("erro", "A senha precisa ter ao menos 6 caracteres.");
                return;
            }
            u.setPassword(passwordEncoder.encode(password));
            usuarioRepository.save(u);
            redirect.addFlashAttribute("sucesso", "Senha de '" + u.getUsername() + "' redefinida.");
        });
        return "redirect:/admin/demos";
    }

    @PostMapping("/demos/{id}/excluir")
    public String excluirDemo(@PathVariable String id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        usuarioRepository.findById(id).filter(u -> u.isContaDemo() || u.isPulaEmail()).ifPresent(u -> {
            usuarioRepository.delete(u); // só contas demo; usuários reais nunca são apagados aqui
            redirect.addFlashAttribute("sucesso", "Conta demo '" + u.getUsername() + "' removida.");
        });
        return "redirect:/admin/demos";
    }
}
