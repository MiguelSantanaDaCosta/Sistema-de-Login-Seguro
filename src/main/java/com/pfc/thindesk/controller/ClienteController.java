package com.pfc.thindesk.controller;

import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

    @PostMapping
    public Cliente novoCliente(@RequestBody Cliente cliente) {
        return clienteService.novoCliente(cliente);
    }

    @GetMapping
    public List<Cliente> listarClientes() {
        return clienteService.listarClientes();
    }
}
