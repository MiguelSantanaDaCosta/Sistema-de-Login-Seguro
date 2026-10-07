package com.pfc.thindesk.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "theme_config")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThemeConfig {

    @Id
    private String id;
    private String temaAtivo = "default";
    private String configuracoesJson;
}
