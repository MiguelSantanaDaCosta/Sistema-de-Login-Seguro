package com.pfc.thindesk.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pfc.thindesk.entity.HorarioAtendimento;
import com.pfc.thindesk.service.HorarioAtendimentoService;

@Controller
@RequestMapping("/ajustes-horarios")
public class HorarioAtendimentoController {

    @Autowired
    private HorarioAtendimentoService horarioAtendimentoService;

    @PostMapping("/salvar")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public String salvarHorario(@jakarta.validation.Valid @ModelAttribute("novoHorario") HorarioAtendimento horarioAtendimento,
            org.springframework.validation.BindingResult erros, org.springframework.ui.Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        if (!erros.hasErrors() && horarioAtendimento.getHorarioInicio().compareTo(horarioAtendimento.getHorarioFim()) >= 0) {
            erros.rejectValue("horarioFim", "intervalo", "O fim deve ser posterior ao início.");
        }
        if (erros.hasErrors()) {
            model.addAttribute("horarios", horarioAtendimentoService.listarTodos());
            model.addAttribute("content", "ajustes-horarios :: content");
            return "layout";
        }
        horarioAtendimento.setId(null);
        horarioAtendimentoService.salvar(horarioAtendimento);
        redirect.addFlashAttribute("sucesso", "Horário cadastrado.");
        return "redirect:/ajustes-horarios";
    }

    @PostMapping("/deletar/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public String deletarHorario(@PathVariable String id) {
        horarioAtendimentoService.deletar(id);
        return "redirect:/ajustes-horarios";
    }
}
