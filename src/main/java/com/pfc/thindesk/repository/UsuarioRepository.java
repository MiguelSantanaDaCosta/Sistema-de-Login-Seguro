package com.pfc.thindesk.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.pfc.thindesk.entity.Usuario;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByUsername(String username);
}
