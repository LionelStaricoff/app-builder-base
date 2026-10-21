package com.openlab.mx.share.identity.entity;

/**
 * Estado propio de la verificación de identidad. El back es source of truth:
 * IN_PROGRESS → VERIFIED | REJECTED | PENDING_MANUAL | EXPIRED,
 * con reintento (restart) desde REJECTED/EXPIRED. VERIFIED es terminal.
 */
public enum VerificationStatus {

    IN_PROGRESS,
    VERIFIED,
    REJECTED,
    PENDING_MANUAL,
    EXPIRED;

    /**
     * Mapea un literal de estado de Didit al estado propio.
     *
     * @throws IllegalArgumentException si el literal no es reconocido
     *         (forward-compatible: los literales futuros se ignoran en el
     *         webhook con {@link #isKnown(String)} antes de llegar acá).
     */
    public static VerificationStatus fromDidit(String diditStatus) {
        if (diditStatus != null) {
            switch (diditStatus) {
                case "Approved":
                    return VERIFIED;
                case "Declined":
                    return REJECTED;
                case "In Review":
                    return PENDING_MANUAL;
                case "Abandoned", "Expired", "Kyc Expired":
                    return EXPIRED;
                case "Not Started", "In Progress", "Awaiting User", "Resubmitted":
                    return IN_PROGRESS;
                default:
                    break;
            }
        }
        throw new IllegalArgumentException("Literal de estado Didit desconocido: " + diditStatus);
    }

    /** true si Didit envió un literal que sabemos mapear. */
    public static boolean isKnown(String diditStatus) {
        if (diditStatus == null) {
            return false;
        }
        switch (diditStatus) {
            case "Approved", "Declined", "In Review", "Abandoned", "Expired", "Kyc Expired",
                 "Not Started", "In Progress", "Awaiting User", "Resubmitted":
                return true;
            default:
                return false;
        }
    }
}
