package com.pfc.thindesk.controller;

import com.pfc.thindesk.entity.HorarioAtendimento;
import com.pfc.thindesk.service.HorarioAtendimentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ajustes-horarios")
public class HorarioAtendimentoController {

    @Autowired
    private HorarioAtendimentoService horarioAtendimentoService;

    @GetMapping
    public String listarHorarios(Model model) {
        model.addAttribute("horarios", horarioAtendimentoService.listarTodos());
        return "ajustes-horarios";
    }

    @PostMapping("/salvar")
    public String salvarHorario(@ModelAttribute HorarioAtendimento horarioAtendimento) {
        horarioAtendimentoService.salvar(horarioAtendimento);
        return "redirect:/ajustes-horarios";
    }

    @GetMapping("/deletar/{id}")
    public String deletarHorario(@PathVariable String id) {
        horarioAtendimentoService.deletar(id);
        return "redirect:/ajustes-horarios";
    }
}
