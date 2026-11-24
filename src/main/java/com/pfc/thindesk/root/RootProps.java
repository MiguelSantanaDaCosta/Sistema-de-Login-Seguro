package com.pfc.thindesk.root;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Chave geral do recurso root. Exposto aos templates como @rootProps. */
@Component("rootProps")
public class RootProps {

    @Value("${app.root.enabled:false}")
    private boolean habilitado;

    @Value("${app.root.puzzles:5}")
    private int puzzles;

    @Value("${app.root.tempo-segundos:300}")
    private int tempoSegundos;

    @Value("${app.root.rating-min:3000}")
    private int ratingMin;

    @Value("${app.root.min-segundos-por-puzzle:4}")
    private int minSegundosPorPuzzle;

    public int getRatingMin() {
        return ratingMin;
    }

    public int getMinSegundosPorPuzzle() {
        return minSegundosPorPuzzle;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public int getPuzzles() {
        return puzzles;
    }

    public int getTempoSegundos() {
        return tempoSegundos;
    }
}
