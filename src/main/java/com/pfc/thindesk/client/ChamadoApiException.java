// Descrição: Falha ao falar com o ms-chamados (indisponível, 4xx ou 5xx).
package com.pfc.thindesk.client;

public class ChamadoApiException extends RuntimeException {

    private final int status;

    public ChamadoApiException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public ChamadoApiException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = 503;
    }

    public int getStatus() {
        return status;
    }
}
