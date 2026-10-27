package com.openlab.mx.share.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.interest.entity.InterestLevel;
import com.openlab.mx.share.proposal.dto.ExperienceProposal;
import com.openlab.mx.share.proposal.dto.InterestItem;
import com.openlab.mx.share.proposal.dto.ProposalRequest;
import com.openlab.mx.share.proposal.generator.StubProposalGenerator;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * El stub es lo que corre en local, tests y e2e: sin key tiene que producir
 * siempre una propuesta válida y determinista.
 */
class StubProposalGeneratorTest {

    private final StubProposalGenerator stub = new StubProposalGenerator();

    private final ProposalRequest request = new ProposalRequest(
            List.of(new InterestItem("Gastronomía", "Tacos al pastor", InterestLevel.CURIOSO)),
            "Mezcla de cocina callejera");

    @Test
    void devuelvePropuestaDeterministica() {
        assertEquals(stub.generate(request), stub.generate(request));
    }

    @Test
    void respetaLimitesDelForm() {
        ExperienceProposal p = stub.generate(request);
        assertFalse(p.name().isBlank());
        assertTrue(p.name().length() <= 255, "name excede VARCHAR(255)");
        assertNotNull(p.includes());
        assertTrue(p.includes().length() <= 2000, "includes excede 2000");
        assertNotNull(p.bring());
        assertTrue(p.bring().length() <= 2000, "bring excede 2000");
        assertEquals(ExperienceCategory.GASTRONOMIA, p.category(),
                "la categoría sale del rubro del interés");
    }

    @Test
    void incluyeElTextoLibreEnLaDescripcion() {
        assertTrue(stub.generate(request).description().contains("Mezcla de cocina callejera"),
                "el texto libre del usuario tiene que verse en la propuesta");
    }

    @Test
    void funcionaSinIntereses() {
        ExperienceProposal p = stub.generate(new ProposalRequest(List.of(), ""));
        assertFalse(p.name().isBlank());
        assertFalse(p.description().isBlank());
        assertNotNull(p.category());
    }
}
