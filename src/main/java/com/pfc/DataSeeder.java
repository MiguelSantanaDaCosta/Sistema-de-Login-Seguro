package com.pfc;

import com.pfc.thindesk.entity.PuzzleXadrez;
import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.PuzzleXadrezRepository;
import com.pfc.thindesk.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PuzzleXadrezRepository puzzleXadrezRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // --- Usuário admin ---
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

        // --- Puzzle inicial ---
        if (puzzleXadrezRepository.count() == 0) {
            PuzzleXadrez puzzle = new PuzzleXadrez();
            puzzle.setFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
            puzzle.setLanceCorreto("e2e4");
            puzzle.setDificuldade("facil");
            puzzle.setDescricao("Abertura do peão do rei");
            puzzleXadrezRepository.save(puzzle);
            System.out.println(">>> Puzzle inicial criado (lanceCorreto=e2e4)");
        }
    }
}
