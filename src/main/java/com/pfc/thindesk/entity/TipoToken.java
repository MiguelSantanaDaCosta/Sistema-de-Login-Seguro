package com.pfc.thindesk.entity;

/** Para que serve o token enviado por email. */
// Tipo do token de e-mail: LOGIN, REGISTRO (confirmar o cadastro) ou REDEFINICAO (esqueci minha senha).
public enum TipoToken {
    LOGIN,
    REGISTRO,
    REDEFINICAO
}
