package com.pfc.thindesk.controller;

import com.pfc.thindesk.service.ThemeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ThemeController {

    @Autowired
    private ThemeService themeService;

    @PostMapping("/tema")
    public String trocarTema(@RequestParam String tema,
                             Authentication auth,
                             @RequestParam(required = false) String redirect) {
        if (auth != null && auth.isAuthenticated()) {
            themeService.salvar(auth.getName(), tema);
        }
        return "redirect:" + (redirect == null ? "/" : redirect);
    }
}
