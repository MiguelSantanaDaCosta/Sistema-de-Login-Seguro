package com.pfc.thindesk.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interruptor das contas de demonstração. As contas NÃO são definidas no código: um ADMIN as cria,
 * redefine e remove em /admin/demos. Com DEMO_ENABLED=false nenhuma conta demo entra sem o login completo.
 * Exposto aos templates como @demoProps.
 */
@Component("demoProps")
public class DemoProps {

    @Value("${app.demo.enabled:false}")
    private boolean habilitado;

    public boolean isHabilitado() {
        return habilitado;
    }
}
