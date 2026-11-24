package com.pfc.thindesk.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Conta de demonstração pública (ver README). Exposta aos templates como @demoProps. */
@Component("demoProps")
public class DemoProps {

    @Value("${app.demo.enabled:false}")
    private boolean habilitado;

    @Value("${app.demo.username:demo}")
    private String username;

    @Value("${app.demo.password:demo1234}")
    private String password;

    @Value("${app.demo.username2:demo2}")
    private String username2;

    @Value("${app.demo.password2:demo2-1234}")
    private String password2;

    public String getUsername2() {
        return username2;
    }

    public String getPassword2() {
        return password2;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
