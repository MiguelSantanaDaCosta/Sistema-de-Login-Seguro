package com.pfc.thindesk.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.pfc.thindesk.dto.RegistroRequest;
import com.pfc.thindesk.entity.Role;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.exception.ConflitoCadastroException;
import com.pfc.thindesk.repository.UsuarioRepository;

@Service
public class UsuarioService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Cadastro feito pelo admin (roles escolhidas por quem chama). */
    public Usuario cadastrar(Usuario usuario, Set<Role> roles) {
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setRoles(roles);
        return usuarioRepository.save(usuario);
    }

    /**
     * Auto-cadastro público. Sempre cria com a role USUARIO (nunca aceita role
     * vinda do cliente).
     * Os formatos já foram validados no controller (@Valid); aqui checamos
     * unicidade.
     */
    public Usuario registrar(RegistroRequest req) {
        String username = req.username.trim();
        String email = req.email.trim().toLowerCase(); // email normalizado em minúsculas

        if (usuarioRepository.existsByUsername(username)) {
            throw new ConflitoCadastroException("Este nome de usuário já está em uso.");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflitoCadastroException("Este email já está cadastrado.");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setEmail(email);
        usuario.setNomeCompleto(req.nomeCompleto.trim());
        usuario.setPassword(passwordEncoder.encode(req.password)); // BCrypt
        usuario.setRoles(Set.of(Role.ROLE_USUARIO));
        usuario.setAtivo(true);
        return usuarioRepository.save(usuario);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        var authorities = usuario.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toList());

        // enabled = ativo: usuário desativado pelo admin não consegue autenticar
        return new org.springframework.security.core.userdetails.User(
                usuario.getUsername(), usuario.getPassword(),
                usuario.isAtivo(), true, true, true, authorities);
    }
}
