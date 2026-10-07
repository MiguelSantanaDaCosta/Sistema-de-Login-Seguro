package com.pfc.thindesk.controller;

import com.pfc.thindesk.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username, @RequestParam String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        
        // Emite token preAuth (2FA pendente)
        String preAuthToken = jwtUtil.gerarToken(username, true);
        return ResponseEntity.ok().header("Authorization", "Bearer " + preAuthToken)
                .body("Credenciais válidas. Resolva o puzzle de xadrez para continuar.");
    }

    @PostMapping("/resolver-puzzle")
    public ResponseEntity<?> resolverPuzzle(@RequestHeader("Authorization") String preAuthToken, @RequestParam String lanceFen) {
        String token = preAuthToken.substring(7);
        String username = jwtUtil.extrairUsername(token);
        
        // Simulação da validação da entidade PuzzleXadrez e/ou API externa C#
        boolean puzzleCorreto = validarLanceDeXadrez(lanceFen); 
        
        if (puzzleCorreto) {
            // Emite o token final (isPreAuth = false)
            String finalToken = jwtUtil.gerarToken(username, false);
            return ResponseEntity.ok().header("Authorization", "Bearer " + finalToken).body("Login concluído.");
        }
        return ResponseEntity.status(403).body("Lance incorreto.");
    }

    private boolean validarLanceDeXadrez(String lanceFen) {
        // Implemente a busca no PuzzleXadrezRepository ou chame o Chess-Engine-Api
        return true; 
    }
}
