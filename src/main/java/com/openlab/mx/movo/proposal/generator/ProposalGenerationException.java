package com.openlab.mx.movo.proposal.generator;

import com.openlab.mx.movo.shared.exception.DomainException;

/**
 * El generador no pudo producir una propuesta válida (modelo caído, JSON
 * inválido tras el reintento). La pantalla la muestra como error recuperable:
 * nunca bloquea al usuario.
 */
public class ProposalGenerationException extends DomainException {

    public ProposalGenerationException(String message) {
        super(message);
    }
}
