package com.pfc.thindesk.exception;

/** Lançada quando o cadastro conflita com um registro existente (username ou email já em uso). */
public class ConflitoCadastroException extends RuntimeException {

    public ConflitoCadastroException(String mensagem) {
        super(mensagem);
    }
}
