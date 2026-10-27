package com.openlab.mx.share.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.interest.entity.InterestLevel;
import com.openlab.mx.share.proposal.dto.ExperienceProposal;
import com.openlab.mx.share.proposal.dto.InterestItem;
import com.openlab.mx.share.proposal.dto.ProposalRequest;
import com.openlab.mx.share.proposal.generator.AiProposalGenerator;
import com.openlab.mx.share.proposal.generator.ProposalGenerationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/**
 * Sin red: el ChatClient está mockeado. Cubre el contrato del spec §4.3 —
 * JSON válido pasa; cualquier respuesta fuera de regla reintenta UNA vez y
 * después falla con ProposalGenerationException (la pantalla lo muestra como
 * error recuperable, nunca como crash).
 */
class AiProposalGeneratorTest {

    private static final ExperienceProposal VALIDA = new ExperienceProposal(
            "Ruta de tacos al pastor",
            "Recorrido por el centro probando seis puestos.",
            ExperienceCategory.GASTRONOMIA,
            "Agua\nGuía",
            "Ropa cómoda");

    private ChatClient chatClient;
    private ChatClient.CallResponseSpec callSpec;
    private AiProposalGenerator generator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        generator = new AiProposalGenerator(chatClient);
    }

    private ProposalRequest request() {
        return new ProposalRequest(
                List.of(new InterestItem("Gastronomía", "Tacos al pastor", InterestLevel.CURIOSO)),
                "Mezcla de cocina callejera");
    }

    @Test
    void jsonValidoDevuelvePropuesta() {
        when(callSpec.entity(ExperienceProposal.class)).thenReturn(VALIDA);

        assertEquals(VALIDA, generator.generate(request()));
    }

    @Test
    void jsonInvalidoReintentaYFalla() {
        when(callSpec.entity(ExperienceProposal.class))
                .thenThrow(new RuntimeException("JSON inválido"))
                .thenThrow(new RuntimeException("JSON inválido"));

        assertThrows(ProposalGenerationException.class, () -> generator.generate(request()));
        verify(callSpec, times(2)).entity(ExperienceProposal.class);
    }

    @Test
    void categoriaInvalidaFalla() {
        ExperienceProposal sinCategoria = new ExperienceProposal(
                "Ruta", "Descripción", null, null, null);
        when(callSpec.entity(ExperienceProposal.class))
                .thenReturn(sinCategoria)
                .thenReturn(sinCategoria);

        assertThrows(ProposalGenerationException.class, () -> generator.generate(request()));
        verify(callSpec, times(2)).entity(ExperienceProposal.class);
    }

    @Test
    void fueraDeLimitesFalla() {
        ExperienceProposal gigante = new ExperienceProposal(
                "Ruta",
                "x".repeat(5000),
                ExperienceCategory.GASTRONOMIA,
                "y".repeat(2500),
                null);
        when(callSpec.entity(ExperienceProposal.class))
                .thenReturn(gigante)
                .thenReturn(gigante);

        assertThrows(ProposalGenerationException.class, () -> generator.generate(request()));
        verify(callSpec, times(2)).entity(ExperienceProposal.class);
    }
}
