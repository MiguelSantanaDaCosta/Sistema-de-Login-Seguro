package com.pfc;

import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Seed inicial: cria o usuário administrador padrão se ele não existir.
 * Os puzzles de xadrez são carregados pelo PuzzleImporter.
 */
// Seed inicial: cria o usuário admin padrão se não existir.
@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @org.springframework.beans.factory.annotation.Value("${app.admin.email:admin@thindesk.local}")
    private String adminEmail;
    @org.springframework.beans.factory.annotation.Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (usuarioRepository.findByUsername("admin").isEmpty()) {
            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setNomeCompleto("Administrador");
            admin.setAtivo(true);
            admin.setRoles(Set.of(Role.ROLE_ADMIN));
            usuarioRepository.save(admin);
            System.out.println(">>> Usuário admin criado.");
        } else {
            sincronizarEmailAdmin();
        }

        // Usuário técnico "root": conta que só é alcançada pelo desafio da rainha vermelha.
        // Senha aleatória e descartada => não existe login por senha para ele.
        if (usuarioRepository.findByUsername("root").isEmpty()) {
            Usuario root = new Usuario();
            root.setUsername("root");
            root.setEmail("root@thindesk.local");
            root.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            root.setNomeCompleto("Root (rainha vermelha)");
            root.setAtivo(true);
            root.setRoles(Set.of(Role.ROLE_ROOT, Role.ROLE_ADMIN, Role.ROLE_TECNICO, Role.ROLE_USUARIO));
            usuarioRepository.save(root);
        }
    }

    // Se o admin ainda tem o email-placeholder (@thindesk.local) e ADMIN_EMAIL é um email real, atualiza.
    private void sincronizarEmailAdmin() {
        if (adminEmail == null || adminEmail.isBlank() || adminEmail.endsWith("@thindesk.local")) {
            return;
        }
        usuarioRepository.findByUsername("admin").ifPresent(admin -> {
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
        });
    }
}
