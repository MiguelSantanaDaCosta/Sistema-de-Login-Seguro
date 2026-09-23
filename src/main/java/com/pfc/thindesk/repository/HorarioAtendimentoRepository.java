package com.pfc.thindesk.repository;

import com.pfc.thindesk.entity.HorarioAtendimento;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HorarioAtendimentoRepository extends MongoRepository<HorarioAtendimento, String> {
    List<HorarioAtendimento> findBySetor(String setor);
}
