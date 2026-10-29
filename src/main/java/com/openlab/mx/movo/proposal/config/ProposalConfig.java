package com.openlab.mx.movo.proposal.config;

import com.openlab.mx.movo.proposal.generator.AiProposalGenerator;
import com.openlab.mx.movo.proposal.generator.ProposalGenerator;
import com.openlab.mx.movo.proposal.generator.StubProposalGenerator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Selección del generador (spec §4.4): el único bean {@link ProposalGenerator}
 * del contexto es stub o IA según el entorno. Regla de oro: NUNCA se rompe el
 * arranque por falta de key — sin provider, sin key o sin ChatClient.Builder
 * se cae al stub con un warn y el flujo de la pantalla 3 sigue funcionando.
 *
 * <p>Orden: ai.proposal.provider=stub (fuerza) → spring.ai.model.chat
 * (none/vacío = stub) → key del proveedor presente (Ollama no usa key) →
 * ChatClient.Builder disponible → IA.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class ProposalConfig {

    @Bean
    public ProposalGenerator proposalGenerator(Environment env, ObjectProvider<ChatClient.Builder> builders) {
        if ("stub".equalsIgnoreCase(env.getProperty("ai.proposal.provider", "auto"))) {
            log.info("[proposal] ai.proposal.provider=stub → stub forzado");
            return new StubProposalGenerator();
        }

        String provider = env.getProperty("spring.ai.model.chat", "none");
        if (provider == null || provider.isBlank() || "none".equalsIgnoreCase(provider)) {
            log.info("[proposal] spring.ai.model.chat=none (sin proveedor) → stub");
            return new StubProposalGenerator();
        }

        if (requiereKey(provider) && keyDe(env, provider).isBlank()) {
            log.warn("[proposal] proveedor '{}' sin API key → stub", provider);
            return new StubProposalGenerator();
        }

        ChatClient.Builder builder = builders.getIfAvailable();
        if (builder == null) {
            log.warn("[proposal] no hay ChatClient.Builder en el contexto (autoconfig "
                    + "de '{}' apagada) → stub", provider);
            return new StubProposalGenerator();
        }

        log.info("[proposal] proveedor '{}' con key → AiProposalGenerator", provider);
        return new AiProposalGenerator(builder.build());
    }

    /** Ollama corre local contra su base-url: no usa API key. */
    private static boolean requiereKey(String provider) {
        return !"ollama".equalsIgnoreCase(provider);
    }

    private static String keyDe(Environment env, String provider) {
        String propiedad = switch (provider.toLowerCase()) {
            case "google-genai" -> "spring.ai.google.genai.api-key";
            case "openai" -> "spring.ai.openai.api-key";
            default -> null;
        };
        if (propiedad == null) {
            // Proveedor desconocido: sin key declarada, stub (no inventamos).
            return "";
        }
        String key = env.getProperty(propiedad);
        return key == null ? "" : key;
    }
}
