package com.openlab.mx.share.shared.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Técnica compartida de normalización de imágenes: redimensiona al
 * {@code maxDimension} (lado mayor, manteniendo proporción) y codifica
 * en JPEG (calidad 0.85, fondo blanco para imágenes con transparencia).
 * Puro JDK (ImageIO), sin dependencias nuevas.
 *
 * <p>Si los bytes no son una imagen legible, se devuelven intactos: el
 * guardado nunca debe romperse por un archivo raro.
 */
public final class ImageNormalizer {

    private static final float JPEG_QUALITY = 0.85f;

    private ImageNormalizer() {
    }

    public static byte[] normalize(byte[] original, int maxDimension) {
        if (original == null || original.length == 0) {
            return original;
        }
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException _) {
            return original;
        }
        if (src == null) {
            return original;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        BufferedImage out = rgb;
        double scale = Math.min(1.0, (double) maxDimension / Math.max(w, h));
        if (scale < 1.0) {
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            Image scaled = rgb.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
            out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(scaled, 0, 0, null);
            } finally {
                g2.dispose();
            }
        }
        try {
            return toJpeg(out);
        } catch (IOException _) {
            return original;
        }
    }

    private static byte[] toJpeg(BufferedImage img) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
