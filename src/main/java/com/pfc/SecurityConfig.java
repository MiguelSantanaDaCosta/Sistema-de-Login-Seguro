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

/**
 * Configuração central de segurança.
 *
 * - Rotas públicas: login, registro, links de confirmação por email, assets
 * - /admin/** -> ROLE_ADMIN
 * - /tecnico/** -> ROLE_TECNICO ou ROLE_ADMIN
 * - resto -> autenticado (qualquer role)
 *
 * O @EnableMethodSecurity permite @PreAuthorize nos controllers (defesa em profundidade).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // Público (o token do link é a credencial das rotas /auth/confirmar*)
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

                        // Todo o resto exige autenticação
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
