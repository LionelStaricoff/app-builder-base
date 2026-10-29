package com.openlab.mx.movo.proposal.service;

import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.Subrubro;
import com.openlab.mx.movo.interest.entity.UserInterest;
import com.openlab.mx.movo.interest.service.InterestService;
import com.openlab.mx.movo.proposal.dto.ExperienceProposal;
import com.openlab.mx.movo.proposal.dto.InterestItem;
import com.openlab.mx.movo.proposal.dto.ProposalForm;
import com.openlab.mx.movo.proposal.dto.ProposalRequest;
import com.openlab.mx.movo.proposal.entity.ExperienceDraft;
import com.openlab.mx.movo.proposal.generator.ProposalGenerator;
import com.openlab.mx.movo.proposal.repository.ExperienceDraftRepository;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Orquestación de la pantalla 3 (spec §3 y §4):
 *
 * <ul>
 *   <li>{@link #generar}: trunca el texto libre a 1000 chars, arma la request
 *       con los intereses del usuario y llama al generador ANTES de cualquier
 *       save — si el generador falla, no se persiste nada a medias.</li>
 *   <li>{@link #guardar}: UPDATE solo cuando el draftParam es un borrador
 *       propio; id ajeno o inválido ⇒ fila propia nueva (el borrador del otro
 *       jamás se toca).</li>
 *   <li>{@link #borradorPropio}: parsea el param String→Long a mano: "abc" o
 *       id gigante ⇒ empty, nunca una excepción de binding 400.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProposalService {

    static final int MAX_TEXTO_LIBRE = 1000;

    private final ProposalGenerator generator;
    private final ExperienceDraftRepository repository;
    private final InterestService interestService;
    private final UserRepository userRepository;

    @Transactional
    public ExperienceDraft generar(User user, String textoLibre) {
        ProposalRequest request = new ProposalRequest(
                interesesDe(user), truncar(textoLibre, MAX_TEXTO_LIBRE));
        ExperienceProposal propuesta = generator.generate(request);
        ExperienceDraft draft = ExperienceDraft.builder()
                .user(user)
                .name(propuesta.name())
                .description(propuesta.description())
                .category(propuesta.category())
                .includes(propuesta.includes())
                .bring(propuesta.bring())
                .build();
        return repository.save(draft);
    }

    @Transactional
    public ExperienceDraft guardar(User user, String draftParam, ProposalForm form) {
        ExperienceDraft draft = borradorPropio(user, draftParam)
                .orElseGet(() -> ExperienceDraft.builder().user(user).build());
        draft.setName(form.name());
        draft.setDescription(form.description());
        draft.setCategory(form.category());
        draft.setIncludes(form.includes());
        draft.setBring(form.bring());
        draft.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return repository.save(draft);
    }

    public Optional<ExperienceDraft> borradorPropio(User user, String draftParam) {
        Long id = parseId(draftParam);
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id)
                .filter(d -> d.getUser() != null && d.getUser().getId().equals(user.getId()));
    }

    public Optional<ExperienceDraft> ultimoBorrador(User user) {
        return repository.findTopByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    @Transactional
    public void marcarOnboarded(User user) {
        user.setProposalOnboarded(true);
        userRepository.save(user);
    }

    /** El nivel vive en la propia UserInterest; no hace falta cruzar niveles(). */
    private List<InterestItem> interesesDe(User user) {
        return interestService.intereses(user).stream()
                .map(this::aInterestItem)
                .toList();
    }

    private InterestItem aInterestItem(UserInterest ui) {
        Subrubro s = ui.getSubrubro();
        return new InterestItem(rubroLabel(s.getCategoria()), s.getNombre(), ui.getLevel());
    }

    /** "GASTRONOMIA" (String de la tabla) → "Gastronomía"; desconocido → el texto tal cual. */
    private static String rubroLabel(String categoria) {
        try {
            return ExperienceCategory.valueOf(categoria).getLabel();
        } catch (IllegalArgumentException | NullPointerException e) {
            return categoria;
        }
    }

    private static String truncar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private static Long parseId(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
