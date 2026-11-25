package com.pfc.thindesk.root;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuração dos desafios (rainha, cavalo e torre). Exposto aos templates como @rootProps. */
@Component("rootProps")
public class RootProps {

    // ---- Rainha vermelha (root) ----
    @Value("${app.root.enabled:false}")
    private boolean habilitado;
    @Value("${app.root.puzzles:5}")
    private int puzzles;
    @Value("${app.root.tempo-segundos:300}")
    private int tempoSegundos;
    @Value("${app.root.rating-min:3000}")
    private int ratingMin;

    /** Resposta mais rápida que isso (em qualquer desafio) é tratada como bot. */
    @Value("${app.root.min-segundos-por-puzzle:4}")
    private int minSegundosPorPuzzle;

    // ---- Desafios de promoção (cavalo verde e torre azul) ----
    @Value("${app.desafio.tempo-segundos:300}")
    private int tempoPromocao;
    @Value("${app.desafio.cavalo.enabled:false}")
    private boolean cavaloHabilitado;
    @Value("${app.desafio.cavalo.puzzles:3}")
    private int cavaloPuzzles;
    @Value("${app.desafio.cavalo.rating-min:1900}")
    private int cavaloMin;
    @Value("${app.desafio.cavalo.rating-max:2600}")
    private int cavaloMax;
    @Value("${app.desafio.torre.enabled:false}")
    private boolean torreHabilitada;
    @Value("${app.desafio.torre.puzzles:5}")
    private int torrePuzzles;
    @Value("${app.desafio.torre.rating-min:2600}")
    private int torreMin;
    @Value("${app.desafio.torre.rating-max:3000}")
    private int torreMax;

    public boolean isHabilitado() {
        return habilitado;
    }

    public int getPuzzles() {
        return puzzles;
    }

    public int getTempoSegundos() {
        return tempoSegundos;
    }

    public int getRatingMin() {
        return ratingMin;
    }

    public int getMinSegundosPorPuzzle() {
        return minSegundosPorPuzzle;
    }

    public boolean isCavaloHabilitado() {
        return cavaloHabilitado;
    }

    public boolean isTorreHabilitada() {
        return torreHabilitada;
    }

    // ---- por tipo ----
    public boolean habilitado(TipoDesafio t) {
        return switch (t) {
            case RAINHA -> habilitado;
            case CAVALO -> cavaloHabilitado;
            case TORRE -> torreHabilitada;
        };
    }

    public int puzzles(TipoDesafio t) {
        return switch (t) {
            case RAINHA -> puzzles;
            case CAVALO -> cavaloPuzzles;
            case TORRE -> torrePuzzles;
        };
    }

    public int tempo(TipoDesafio t) {
        return t == TipoDesafio.RAINHA ? tempoSegundos : tempoPromocao;
    }

    /** Rating mínimo do desafio. */
    public int min(TipoDesafio t) {
        return switch (t) {
            case RAINHA -> ratingMin;
            case CAVALO -> cavaloMin;
            case TORRE -> torreMin;
        };
    }

    /** Rating máximo do desafio (a rainha não tem teto). */
    public Integer max(TipoDesafio t) {
        return switch (t) {
            case RAINHA -> null;
            case CAVALO -> cavaloMax;
            case TORRE -> torreMax;
        };
    }
}
