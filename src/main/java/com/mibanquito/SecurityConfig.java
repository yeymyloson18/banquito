package com.mibanquito;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion minima para habilitar seguridad por metodo (@PreAuthorize,
 * FR-014). El modulo Autenticacion (fuera de alcance de 001-aportes) debera
 * reemplazar/ampliar esto con el flujo real de login y el origen de roles
 * (constitucion Principio IX). Mientras tanto, la capa HTTP queda abierta y
 * la restriccion real ocurre a nivel de metodo con el Authentication que
 * exista en el contexto (anonimo por defecto, lo que ya niega hasRole(...)).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
