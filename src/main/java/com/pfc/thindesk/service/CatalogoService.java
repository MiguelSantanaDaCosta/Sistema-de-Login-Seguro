package com.pfc.thindesk.service;

import java.util.List;
import com.pfc.thindesk.entity.ItemCatalogo;
import com.pfc.thindesk.repository.ItemCatalogoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogoService {
    private final ItemCatalogoRepository repository;

    public CatalogoService(ItemCatalogoRepository repository) {
        this.repository = repository;
    }

    public List<ItemCatalogo> listar(String categoria) {
        return repository.findByCategoriaOrderByNomeAsc(categoria);
    }

    public ItemCatalogo buscar(String categoria, String id) {
        return repository.findById(id).filter(i -> categoria.equals(i.getCategoria()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado."));
    }

    public void salvar(String categoria, String id, ItemCatalogo dados) {
        ItemCatalogo item = id == null ? new ItemCatalogo() : buscar(categoria, id);
        item.setCategoria(categoria);
        item.setNome(dados.getNome().trim());
        item.setDescricao(dados.getDescricao() == null ? "" : dados.getDescricao().trim());
        repository.save(item);
    }

    public void excluir(String categoria, String id) {
        repository.delete(buscar(categoria, id));
    }
}
