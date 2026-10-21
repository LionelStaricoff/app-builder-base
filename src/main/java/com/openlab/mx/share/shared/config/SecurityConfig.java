package com.openlab.mx.share.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login", "/register",
                    "/manifest.webmanifest", "/sw.js",
                    "/css/**", "/js/**", "/icons/**", "/img/**", "/vendor/**", "/error"
                ).permitAll()
                // Spec §7: Stripe no firma requests con sesión ni CSRF.
                .requestMatchers("/api/stripe/webhooks").permitAll()
                // Didit entrega el webhook sin sesión: la autenticación es
                // la firma HMAC (X-Signature-V2) y no lleva CSRF.
                .requestMatchers("/api/webhooks/didit").permitAll()
                .anyRequest().authenticated()
            )
            // Solo los webhooks y el canal del chat quedan fuera de CSRF.
            // Los webhooks se autentican con firma (Stripe / HMAC de Didit), no
            // con sesión. /api/didit/sessions NO va acá: es un POST de sesión
            // autenticada y el front manda el token (didit-verification.js).
            //
            // /ws/** es el canal STOMP del chat: el cliente solo RECIBE (los
            // mensajes se mandan por POST HTTP normal, con token). SockJS puede
            // caer al transporte xhr-streaming, que hace POSTs, y sin esta
            // excepción el CsrfFilter los rechazaría con 403 y el chat no
            // conectaría. La autorización la hace WebSocketAuthInterceptor
            // (sesión + permiso de suscripción).
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    "/api/stripe/webhooks", "/api/webhooks/didit", "/ws/**"))
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/feed", true)
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
