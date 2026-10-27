package com.openlab.mx.share.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.interest.service.InterestService;
import com.openlab.mx.share.proposal.dto.ExperienceProposal;
import com.openlab.mx.share.proposal.dto.ProposalRequest;
import com.openlab.mx.share.proposal.generator.ProposalGenerationException;
import com.openlab.mx.share.proposal.generator.ProposalGenerator;
import com.openlab.mx.share.proposal.repository.ExperienceDraftRepository;
import com.openlab.mx.share.proposal.service.ProposalService;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

/**
 * Semántica de borradores y degradación del servicio (spec §3 y §4.4):
 * truncar el texto, no persistir nada si el generador falla, y parsear el
 * draftParam a mano para que un "abc" jamás llegue a una excepción 400.
 */
@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    /** Fake sin red que captura la request y puede fallar a pedido. */
    private static class GeneradorFake implements ProposalGenerator {
        ProposalRequest capturado;
        boolean fallar;

        @Override
        public ExperienceProposal generate(ProposalRequest request) {
            this.capturado = request;
            if (fallar) {
                throw new ProposalGenerationException("fallo del generador");
            }
            return new ExperienceProposal("Ruta de prueba", "Descripción de prueba",
                    ExperienceCategory.GASTRONOMIA, "Agua", "Ropa cómoda");
        }
    }

    @Mock
    private ExperienceDraftRepository repository;
    @Mock
    private InterestService interestService;
    @Mock
    private UserRepository userRepository;

    private GeneradorFake generator;
    private ProposalService service;

    @BeforeEach
    void setUp() {
        generator = new GeneradorFake();
        service = new ProposalService(generator, repository, interestService, userRepository);
    }

    private User user() {
        return User.builder().id(7L).username("u").build();
    }

    @Test
    void truncaTextoLibreALos1000Chars() {
        when(interestService.intereses(any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generar(user(), "x".repeat(100_000));

        assertEquals(1000, generator.capturado.textoLibre().length(),
                "el texto libre se trunca a 1000 antes de llegar al generador");
    }

    @Test
    void generarSinInteresesNoFalla() {
        when(interestService.intereses(any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var draft = service.generar(user(), null);

        assertTrue(generator.capturado.intereses().isEmpty());
        assertEquals("Ruta de prueba", draft.getName());
        verify(repository).save(any());
    }

    @Test
    void generarFallidoNoGuardaNada() {
        when(interestService.intereses(any())).thenReturn(List.of());
        generator.fallar = true;

        assertThrows(ProposalGenerationException.class, () -> service.generar(user(), "texto"));
        verify(repository, never()).save(any());
    }

    @Test
    void guardarConDraftAjenoCreaFilaNueva() {
        User propio = user();
        User ajeno = User.builder().id(99L).username("otro").build();
        var draftAjeno = com.openlab.mx.share.proposal.entity.ExperienceDraft.builder()
                .id(77L).user(ajeno).name("Ajeno").description("D")
                .category(ExperienceCategory.CULTURAL).build();
        when(repository.findById(77L)).thenReturn(Optional.of(draftAjeno));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var form = new com.openlab.mx.share.proposal.dto.ProposalForm(
                "Mío", "Descripción mía", ExperienceCategory.AVENTURA, null, null);
        var guardado = service.guardar(propio, "77", form);

        assertEquals(propio.getId(), guardado.getUser().getId(),
                "el borrador ajeno no se toca: se crea uno propio");
        assertEquals("Mío", guardado.getName());
    }

    @Test
    void borradorPropioIgnoraParametroInvalido() {
        assertTrue(service.borradorPropio(user(), "abc").isEmpty(), "sin 400, sin excepción");
        assertTrue(service.borradorPropio(user(), null).isEmpty());
        assertTrue(service.borradorPropio(user(), "").isEmpty());
        assertFalse(service.borradorPropio(user(), "999999999999").isPresent()
                        && generator.capturado != null,
                "id gigante inexistente → empty");
    }
}
