package com.openlab.mx.movo.proposal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openlab.mx.movo.proposal.config.ProposalConfig;
import com.openlab.mx.movo.proposal.generator.AiProposalGenerator;
import com.openlab.mx.movo.proposal.generator.ProposalGenerator;
import com.openlab.mx.movo.proposal.generator.StubProposalGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Selección de beans (spec §4.4): sin provider/key/builder el flujo SIEMPRE
 * sigue vivo con el stub — esta es la degradación pedida, testeada caso a caso.
 */
class ProposalConfigTest {

    private final ProposalConfig config = new ProposalConfig();

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatClient.Builder> builders(ChatClient.Builder builder) {
        ObjectProvider<ChatClient.Builder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(builder);
        return provider;
    }

    private ChatClient.Builder chatBuilder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return builder;
    }

    private Environment env(String... claveValor) {
        MockEnvironment env = new MockEnvironment();
        for (int i = 0; i < claveValor.length; i += 2) {
            env.setProperty(claveValor[i], claveValor[i + 1]);
        }
        return env;
    }

    @Test
    void providerNoneDaStub() {
        ProposalGenerator sinPropiedad = config.proposalGenerator(env(), builders(chatBuilder()));
        ProposalGenerator none = config.proposalGenerator(
                env("spring.ai.model.chat", "none"), builders(chatBuilder()));

        assertTrue(sinPropiedad instanceof StubProposalGenerator,
                "sin spring.ai.model.chat el default es stub (arranque sin key)");
        assertTrue(none instanceof StubProposalGenerator);
    }

    @Test
    void googleGenAiConKeyYBuilderDaIa() {
        ProposalGenerator g = config.proposalGenerator(
                env("spring.ai.model.chat", "google-genai",
                        "spring.ai.google.genai.api-key", "k-test"),
                builders(chatBuilder()));

        assertTrue(g instanceof AiProposalGenerator);
    }

    @Test
    void googleGenAiSinKeyDaStub() {
        ProposalGenerator g = config.proposalGenerator(
                env("spring.ai.model.chat", "google-genai"),
                builders(chatBuilder()));

        assertTrue(g instanceof StubProposalGenerator,
                "proveedor con key requerida y sin key → degrada a stub, nunca rompe");
    }

    @Test
    void ollamaSinKeyDaIa() {
        ProposalGenerator g = config.proposalGenerator(
                env("spring.ai.model.chat", "ollama"),
                builders(chatBuilder()));

        assertTrue(g instanceof AiProposalGenerator,
                "Ollama corre local: no necesita key");
    }

    @Test
    void providerStubFuerzaStub() {
        ProposalGenerator g = config.proposalGenerator(
                env("ai.proposal.provider", "stub",
                        "spring.ai.model.chat", "google-genai",
                        "spring.ai.google.genai.api-key", "k-test"),
                builders(chatBuilder()));

        assertTrue(g instanceof StubProposalGenerator);
    }

    @Test
    void sinBuilderDaStub() {
        ProposalGenerator g = config.proposalGenerator(
                env("spring.ai.model.chat", "google-genai",
                        "spring.ai.google.genai.api-key", "k-test"),
                builders(null));

        assertTrue(g instanceof StubProposalGenerator,
                "sin ChatClient.Builder (autoconfig apagada) → stub");
    }
}
