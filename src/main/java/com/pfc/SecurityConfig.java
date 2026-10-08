// ============================================================
// SecurityConfig.java
// Autor: Miguel Santana
// Descrição: Regras de acesso e encoder de senha.
// ============================================================
package com.pfc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.pfc.thindesk.security.JwtAuthFilter;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize nos controllers
public class SecurityConfig {

    // BCrypt para guardar senha
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Exposto para o AuthController autenticar usuário/senha
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // API stateless: não usa CSRF
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Forward interno para /error não deve ser bloqueado
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // Rotas públicas (login, cadastro, confirmação por email, assets)
                        .requestMatchers(
                                "/api/auth/**",
                                "/login",
                                "/registrar",
                                "/puzzle",
                                "/auth/confirmar",
                                "/auth/confirmar-registro",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/error")
                        .permitAll()

                        // Áreas HTML por perfil
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/tecnico/**").hasAnyRole("TECNICO", "ADMIN")

                        // APIs por perfil
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/tecnico/**").hasAnyRole("TECNICO", "ADMIN")

                        // Resto exige login
                        .anyRequest().authenticated())

                // Filtro JWT antes do filtro padrão
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
