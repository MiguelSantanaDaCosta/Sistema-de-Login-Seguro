package com.pfc.thindesk.service;

import com.pfc.thindesk.entity.Chamado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import com.pfc.thindesk.repository.ChamadoRepository;

@Service
public class ChamadoService {

    @Autowired
    private ChamadoRepository chamadoRepository;

    public Chamado criarChamado(Chamado chamado) {
        return chamadoRepository.save(chamado);
    }

    public List<Chamado> listarChamados() {
        return chamadoRepository.findAll();
    }

  public Chamado atualizarChamado(String id, Chamado atualizado) {
    return chamadoRepository.findById(id)
        .map(c -> {
            if (atualizado.getDescricao() != null) c.setDescricao(atualizado.getDescricao());
            if (atualizado.getStatus()    != null) c.setStatus(atualizado.getStatus());
            if (atualizado.getTipo()      != null) c.setTipo(atualizado.getTipo());
            if (atualizado.getTecnico()   != null) c.setTecnico(atualizado.getTecnico());
            if (atualizado.getUsuario()   != null) c.setUsuario(atualizado.getUsuario());
            return chamadoRepository.save(c);
        })
        .orElseThrow(() -> new RuntimeException("Chamado não encontrado: " + id));
} 

}
