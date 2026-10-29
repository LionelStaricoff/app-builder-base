package com.openlab.mx.movo.proposal.generator;

import com.openlab.mx.movo.proposal.dto.ExperienceProposal;
import com.openlab.mx.movo.proposal.dto.ProposalRequest;

/**
 * Puerto de generación de propuestas (spec §4.1): lo implementan el stub
 * determinista (local/tests, sin key) y la versión con Spring AI.
 */
public interface ProposalGenerator {

    ExperienceProposal generate(ProposalRequest request);
}
