package com.pfc.thindesk.repository;

import com.pfc.thindesk.entity.Chamado;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChamadoRepository extends MongoRepository<Chamado, String> {
    List<Chamado> findByStatus(String status);
    
    
}
