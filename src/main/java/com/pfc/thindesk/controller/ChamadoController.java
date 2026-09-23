package com.pfc.thindesk.controller;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.service.ChamadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;

@RestController
@RequestMapping("/api/chamados")
public class ChamadoController {

    @Autowired
    private ChamadoService chamadoService;

    @PostMapping
    public Chamado criarChamado(@RequestBody Chamado chamado) {
        return chamadoService.criarChamado(chamado);
    }

    @GetMapping
    public List<Chamado> listarChamados() {
        return chamadoService.listarChamados();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Chamado> atualizarChamado(@PathVariable String id, @RequestBody Chamado chamadoAtualizado) {
        Chamado chamado = chamadoService.atualizarChamado(id, chamadoAtualizado);
        return ResponseEntity.ok(chamado);
    }

    @PostMapping("/atualizar/{id}")
    public String atualizarChamado(@PathVariable String id, @ModelAttribute Chamado chamadoAtualizado, Model model) {
        chamadoService.atualizarChamado(id, chamadoAtualizado);
        return "redirect:/chamados"; // Redireciona para a página da lista de chamados
    }
}
