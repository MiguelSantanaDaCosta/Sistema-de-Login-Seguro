package com.pfc.thindesk.service;

import com.pfc.thindesk.entity.ThemeConfig;
import com.pfc.thindesk.repository.ThemeConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ThemeService {

    public static final String DEFAULT_THEME = "default";

    @Autowired
    private ThemeConfigRepository repository;

    /** Retorna o tema do usuário ou o padrão se não houver config. */
    public String temaDoUsuario(String username) {
        if (username == null) return DEFAULT_THEME;
        return repository.findByUsername(username)
                .map(ThemeConfig::getTemaAtivo)
                .orElse(DEFAULT_THEME);
    }

    public ThemeConfig salvar(String username, String tema) {
        ThemeConfig cfg = repository.findByUsername(username)
                .orElseGet(() -> {
                    ThemeConfig t = new ThemeConfig();
                    t.setUsername(username);
                    return t;
                });
        cfg.setTemaAtivo(tema);
        return repository.save(cfg);
    }
}
