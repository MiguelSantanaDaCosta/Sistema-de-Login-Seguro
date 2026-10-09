package com.pfc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.pfc.thindesk.service.ThemeService;

/**
 * Injeta em TODA view renderizada por um @Controller:
 *   - temaAtivo → o tema salvo do usuário logado (ou null se anônimo)
 *   - username  → o nome do usuário logado (ou null)
 *
 * Isso evita repetir `model.addAttribute("temaAtivo", ...)` em cada controller.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private ThemeService themeService;

    @ModelAttribute("temaAtivo")
    public String temaAtivo(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return themeService.temaDoUsuario(auth.getName());
    }

    @ModelAttribute("username")
    public String username(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }
}
