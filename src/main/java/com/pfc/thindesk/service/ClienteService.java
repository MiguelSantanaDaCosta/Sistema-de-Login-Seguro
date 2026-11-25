package com.pfc.thindesk.service;

import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    public Cliente novoCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Cliente buscar(String id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    public Cliente atualizar(String id, Cliente dados) {
        Cliente atual = buscar(id);
        atual.setNome(dados.getNome().trim());
        atual.setTelefone(dados.getTelefone());
        atual.setSetor(dados.getSetor().trim());
        return clienteRepository.save(atual);
    }

    public void excluir(String id) {
        clienteRepository.delete(buscar(id));
    }
}
