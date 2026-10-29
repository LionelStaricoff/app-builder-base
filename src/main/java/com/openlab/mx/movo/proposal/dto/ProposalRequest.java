package com.openlab.mx.movo.proposal.dto;

import java.util.List;

/** Entrada del generador: intereses del usuario + texto libre opcional. */
public record ProposalRequest(List<InterestItem> intereses, String textoLibre) {
}
