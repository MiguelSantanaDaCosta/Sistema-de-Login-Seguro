package com.pfc.thindesk.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.pfc.thindesk.client.ChamadoApiException;
import com.pfc.thindesk.client.ChamadoClient;
import com.pfc.thindesk.client.ChamadoMs;

/**
 * Dashboard (página inicial): resumo dos chamados do usuário logado e, para TECNICO/ADMIN,
 * a fila de chamados abertos de todos. Os dados vêm do ms-chamados.
 */
@Controller
public class DashboardController {

    @Autowired
    private ChamadoClient chamadoClient;
    @Autowired
    private com.pfc.thindesk.analise.PuzzleAnaliseService analiseService;

    @GetMapping("/")
    public String dashboard(Model model, Authentication auth,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String promovido) {
        boolean tecnico = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TECNICO") || a.getAuthority().equals("ROLE_ADMIN"));

        List<ChamadoMs> meus = List.of();
        List<ChamadoMs> fila = List.of();
        String erro = null;
        try {
            meus = chamadoClient.listarDoUsuario(auth.getName());
            if (tecnico) {
                fila = chamadoClient.listarAbertos();
            }
        } catch (ChamadoApiException e) {
            erro = e.getMessage();
        }

        long abertos = meus.stream().filter(c -> "ABERTO".equals(c.status())).count();
        long resolvidos = meus.stream().filter(c -> "RESOLVIDO".equals(c.status())).count();
        long total = meus.size();

        model.addAttribute("nome", auth.getName());
        model.addAttribute("tecnico", tecnico);
        model.addAttribute("total", total);
        model.addAttribute("abertos", abertos);
        model.addAttribute("resolvidos", resolvidos);
        model.addAttribute("percentualResolvidos", total == 0 ? 0 : Math.round(resolvidos * 100.0 / total));
        model.addAttribute("recentes", meus.stream()
                .sorted(Comparator.comparing(ChamadoMs::dataAbertura, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5).toList());
        model.addAttribute("fila", fila.stream().limit(5).toList());
        model.addAttribute("filaTotal", fila.size());
        model.addAttribute("erro", erro);
        // aviso após vencer um desafio de promoção (cavalo -> tecnico, torre -> admin)
        model.addAttribute("promovido", switch (promovido == null ? "" : promovido) {
            case "tecnico" -> "Técnico";
            case "admin" -> "Administrador";
            default -> null;
        });
        model.addAttribute("analise", analiseService.resumo());
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
