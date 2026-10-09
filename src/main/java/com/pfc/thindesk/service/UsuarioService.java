package com.pfc.thindesk.service;

import java.util.Optional;
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
import com.pfc.thindesk.entity.TipoToken;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.exception.ConflitoCadastroException;
import com.pfc.thindesk.repository.UsuarioRepository;

@Service
public class UsuarioService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailTokenService emailTokenService;

    @Autowired
    private EmailService emailService;

    /** Cadastro feito pelo admin (usuário já ativo e confirmado). */
    public Usuario cadastrar(Usuario usuario, Set<Role> roles) {
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setRoles(roles);
        usuario.setAtivo(true);
        usuario.setEmailConfirmado(true);
        return usuarioRepository.save(usuario);
    }

    /**
     * Etapa 1 do cadastro público:
     * - cria usuário PENDENTE (ativo=false, emailConfirmado=false)
     * - gera token de registro (uso único, com TTL) e envia por email
     * - devolve o token bruto (usado pelo RegistroController para o modo dev)
     */
    // Etapa 1 do cadastro público: cria usuário pendente e envia token por e-mail.
    public String registrar(RegistroRequest req) {
        String username = req.username.trim();
        String email = req.email.trim().toLowerCase(); // email sempre em minúsculas

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
        usuario.setAtivo(false); // só ativa após clicar no link
        usuario.setEmailConfirmado(false);
        usuarioRepository.save(usuario);

        String token = emailTokenService.gerar(username, TipoToken.REGISTRO);
        emailService.enviarTokenRegistro(email, token);
        return token;
    }

    /**
     * Etapa 2 do cadastro público: consome o token de REGISTRO e ativa a conta.
     * Retorna true se o token era válido; false caso contrário.
     */
    // Etapa 2: consome o token de registro e ativa a conta.
    public boolean confirmarRegistro(String token) {
        Optional<String> dono = emailTokenService.consumir(token, TipoToken.REGISTRO);
        if (dono.isEmpty()) {
            return false;
        }
        return usuarioRepository.findByUsername(dono.get())
                .map(u -> {
                    u.setEmailConfirmado(true);
                    u.setAtivo(true);
                    usuarioRepository.save(u);
                    return true;
                })
                .orElse(false);
    }

    // Carrega o usuário para autenticação (ativo e roles).
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        var authorities = usuario.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toList());

        // enabled = ativo: usuário pendente (não confirmou email) ou desativado pelo
        // admin não autentica
        return new org.springframework.security.core.userdetails.User(
                usuario.getUsername(), usuario.getPassword(),
                usuario.isAtivo(), true, true, true, authorities);
    }
}
