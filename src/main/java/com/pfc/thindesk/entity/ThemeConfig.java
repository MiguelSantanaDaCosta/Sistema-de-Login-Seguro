package com.pfc.thindesk.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Configuração de tema aplicada por usuário.
 * O campo `temaAtivo` é a chave que vira o atributo data-theme no <body>.
 * Temas suportados hoje: "default", "dark", "high-contrast".
 * Para adicionar novos, basta criar o CSS correspondente em /css/themes/{chave}.css
 * e registrar a chave no front (ver fragmento `theme-switcher`).
 */
@Document(collection = "theme_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThemeConfig {

    @Id
    private String id;

    /** username do dono — 1 config por usuário */
    private String username;

    /** chave do tema ativo: default | dark | high-contrast */
    private String temaAtivo = "default";

    /** JSON livre para customizações futuras (cores, logo, etc.) */
    private String configuracoesJson;
}
