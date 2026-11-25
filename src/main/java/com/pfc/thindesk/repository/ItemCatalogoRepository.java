package com.pfc.thindesk.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.pfc.thindesk.entity.ItemCatalogo;

public interface ItemCatalogoRepository extends MongoRepository<ItemCatalogo, String> {
    List<ItemCatalogo> findByCategoriaOrderByNomeAsc(String categoria);
}
