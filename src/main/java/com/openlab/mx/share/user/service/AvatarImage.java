package com.openlab.mx.share.user.service;

import com.openlab.mx.share.shared.image.ImageNormalizer;

/**
 * Política del avatar de perfil: máx 512px en JPEG (ver
 * {@link ImageNormalizer} para la técnica). Fachada de dominio para que
 * el controller no maneje números mágicos.
 */
public final class AvatarImage {

    public static final int MAX_DIMENSION = 512;

    private AvatarImage() {
    }

    public static byte[] normalize(byte[] original) {
        return ImageNormalizer.normalize(original, MAX_DIMENSION);
    }
}
