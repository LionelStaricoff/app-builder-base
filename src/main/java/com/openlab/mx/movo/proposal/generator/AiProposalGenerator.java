package com.openlab.mx.movo.proposal.generator;

import com.openlab.mx.movo.proposal.dto.ExperienceProposal;
import com.openlab.mx.movo.proposal.dto.InterestItem;
import com.openlab.mx.movo.proposal.dto.ProposalRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Generador con Spring AI (spec §4.3): prompt en español con salida
 * estructurada (JSON → ExperienceProposal vía entity()) y validación de
 * dominio post-parse. Un reintento automático; si el modelo vuelve a fallar
 * (red, JSON irreparable, campos fuera de regla) lanza
 * {@link ProposalGenerationException}, que la pantalla muestra como error
 * recuperable con "Generar propuesta" (Reintentar) y "Saltear".
 *
 * <p>Límite de description = 2000: el form no pone @Size en description,
 * pero acá es el techo contra salidas descontroladas del modelo (igual que
 * el techo que aplica el stub).
 */
@Slf4j
public class AiProposalGenerator implements ProposalGenerator {

    static final int MAX_NOMBRE = 255;
    static final int MAX_DESCRIPCION = 2000;
    static final int MAX_TEXTO = 2000;

    private static final int INTENTOS = 2;

    private static final String SISTEMA = """
            Sos anfitrión de experiencias locales en México. Respondés SOLO con un
            objeto JSON con exactamente estos campos:
            {"name": "...", "description": "...", "category": "...",
             "includes": "...", "bring": "..."}
            Reglas: name y description no vacíos (description máximo 2000
            caracteres); category es UNA de: GASTRONOMIA, CULTURAL, AVENTURA,
            NATURALEZA, DEPORTES, ARTE, SOCIAL, RELAX; includes y bring son
            listas con un ítem por línea, máximo 2000 caracteres cada una.
            Escribí en español neutro. No inventes fechas, precios ni cupos:
            esos datos los completa la persona después.
            """;

    private final ChatClient chatClient;

    public AiProposalGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ExperienceProposal generate(ProposalRequest request) {
        long inicio = System.currentTimeMillis();
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                ExperienceProposal propuesta = chatClient.prompt()
                        .system(SISTEMA)
                        .user(textoDe(request))
                        .call()
                        .entity(ExperienceProposal.class);
                validar(propuesta);
                log.info("[proposal] generada en {} ms (intento {})",
                        System.currentTimeMillis() - inicio, intento);
                return propuesta;
            } catch (RuntimeException e) {
                log.warn("[proposal] intento {} falló: {}", intento, e.getMessage());
            }
        }
        log.error("[proposal] agotados {} intentos en {} ms", INTENTOS,
                System.currentTimeMillis() - inicio);
        throw new ProposalGenerationException("propuesta inválida del modelo");
    }

    private static String textoDe(ProposalRequest request) {
        StringBuilder sb = new StringBuilder("Intereses del usuario:\n");
        for (InterestItem item : request.intereses()) {
            sb.append("- ").append(item.rubroLabel())
                    .append(" / ").append(item.subrubroNombre())
                    .append(" (").append(item.nivel()).append(")\n");
        }
        String texto = request.textoLibre() == null ? "" : request.textoLibre().trim();
        if (!texto.isEmpty()) {
            sb.append("\nLo que quiere ofrecer:\n").append(texto).append('\n');
        }
        return sb.toString();
    }

    /** Validación de dominio: lo mismo que exige el form de alta. */
    private static void validar(ExperienceProposal p) {
        if (p == null) {
            throw new IllegalStateException("propuesta nula");
        }
        if (p.name() == null || p.name().isBlank() || p.name().length() > MAX_NOMBRE) {
            throw new IllegalStateException("name fuera de regla");
        }
        if (p.description() == null || p.description().isBlank()
                || p.description().length() > MAX_DESCRIPCION) {
            throw new IllegalStateException("description fuera de regla");
        }
        if (p.category() == null) {
            throw new IllegalStateException("category fuera de regla");
        }
        if (p.includes() != null && p.includes().length() > MAX_TEXTO) {
            throw new IllegalStateException("includes fuera de regla");
        }
        if (p.bring() != null && p.bring().length() > MAX_TEXTO) {
            throw new IllegalStateException("bring fuera de regla");
        }
    }
}
