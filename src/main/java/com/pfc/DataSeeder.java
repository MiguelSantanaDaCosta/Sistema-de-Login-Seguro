package com.pfc;

import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Seed inicial: garante o administrador (usuário/senha/email vindos do ambiente) e o usuário técnico "root".
 * Contas de demonstração NÃO são criadas aqui: um ADMIN as gerencia em /admin/demos.
 * Os puzzles de xadrez são carregados pelo PuzzleImporter.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.email:admin@thindesk.local}")
    private String adminEmail;
    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        garantirAdmin();
        garantirRoot();
    }

    private void garantirAdmin() {
        Optional<Usuario> atual = usuarioRepository.findByUsername(adminUsername);
        if (atual.isPresent()) {
            sincronizarEmailAdmin(atual.get());
            return;
        }

        // ADMIN_USERNAME mudou: promove o admin padrão antigo ("admin"), em vez de deixar a senha padrão no ar
        Optional<Usuario> legado = "admin".equals(adminUsername) ? Optional.empty()
                : usuarioRepository.findByUsername("admin")
                        .filter(u -> u.getRoles().contains(Role.ROLE_ADMIN) && !u.isContaDemo() && !u.isPulaEmail());
        if (legado.isPresent()) {
            Usuario u = legado.get();
            u.setUsername(adminUsername);
            u.setPassword(passwordEncoder.encode(adminPassword));
            if (adminEmail != null && !adminEmail.isBlank() && !adminEmail.endsWith("@thindesk.local")) {
                u.setEmail(adminEmail);
            }
            try {
                usuarioRepository.save(u);
                System.out.println(">>> Admin padrão renomeado para '" + adminUsername + "' e senha redefinida pelo ambiente.");
            } catch (RuntimeException e) {
                System.out.println(">>> Não foi possível atualizar admin padrão: " + e.getMessage());
            }
            return;
        }

        Usuario admin = new Usuario();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNomeCompleto("Administrador");
        admin.setAtivo(true);
        admin.setRoles(Set.of(Role.ROLE_ADMIN));
        try {
            usuarioRepository.save(admin);
            System.out.println(">>> Usuário admin '" + adminUsername + "' criado.");
        } catch (RuntimeException e) {
            System.out.println(">>> Não foi possível criar admin: " + e.getMessage());
        }
    }

    // Se o admin ainda tem o email-placeholder (@thindesk.local) e ADMIN_EMAIL é um email real, atualiza.
    private void sincronizarEmailAdmin(Usuario admin) {
        if (adminEmail == null || adminEmail.isBlank() || adminEmail.endsWith("@thindesk.local")) {
            return;
        }
        String atual = admin.getEmail();
        if (atual != null && atual.endsWith("@thindesk.local") && !atual.equalsIgnoreCase(adminEmail)) {
            try {
                admin.setEmail(adminEmail);
                usuarioRepository.save(admin);
                System.out.println(">>> Email do admin atualizado a partir de ADMIN_EMAIL.");
            } catch (RuntimeException e) {
                System.out.println(">>> Não foi possível atualizar o email do admin: " + e.getMessage());
            }
        }
    }

    // Usuário técnico "root": conta que só é alcançada pelo desafio da rainha vermelha.
    // Senha aleatória e descartada => não existe login por senha para ele.
    private void garantirRoot() {
        if (usuarioRepository.findByUsername("root").isEmpty()) {
            Usuario root = new Usuario();
            root.setUsername("root");
            root.setEmail("root@thindesk.local");
            root.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            root.setNomeCompleto("Root (rainha vermelha)");
            root.setAtivo(true);
            root.setRoles(Set.of(Role.ROLE_ROOT, Role.ROLE_ADMIN, Role.ROLE_TECNICO, Role.ROLE_USUARIO));
            try {
                usuarioRepository.save(root);
                System.out.println(">>> Usuário root criado.");
            } catch (RuntimeException e) {
                System.out.println(">>> Não foi possível criar root: " + e.getMessage());
            }
        }
    }
}
