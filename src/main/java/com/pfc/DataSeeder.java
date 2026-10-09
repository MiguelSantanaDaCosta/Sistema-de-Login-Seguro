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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (usuarioRepository.findByUsername("admin").isEmpty()) {
            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setEmail("admin@thindesk.local");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNomeCompleto("Administrador");
            admin.setAtivo(true);
            admin.setRoles(Set.of(Role.ROLE_ADMIN));
            usuarioRepository.save(admin);
            System.out.println(">>> Usuário admin criado: admin / admin123");
        }
    }
}
