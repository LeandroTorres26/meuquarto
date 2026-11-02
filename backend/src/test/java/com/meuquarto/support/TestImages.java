package com.meuquarto.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Builds small pictures for the avatar tests, so no binary fixture has to be committed. */
public final class TestImages {

    private TestImages() {}

    /** A picture with the left half in {@code left} and the right half in {@code right}. */
    public static BufferedImage halves(int width, int height, Color left, Color right) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(left);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(right);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    /** A picture with the top half in {@code top} and the bottom half in {@code bottom}. */
    public static BufferedImage stacked(int width, int height, Color top, Color bottom) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(top);
        g.fillRect(0, 0, width, height / 2);
        g.setColor(bottom);
        g.fillRect(0, height / 2, width, height - height / 2);
        g.dispose();
        return image;
    }

    public static byte[] png(BufferedImage image) {
        return write(image, "png");
    }

    /** A JPEG (alpha is dropped first, as the JPEG writer cannot take it). */
    public static byte[] jpeg(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return write(rgb, "jpeg");
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage decode(byte[] content) {
        try {
            return ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Inserts a segment right after the JPEG's start-of-image marker. {@code marker} is the second byte of
     * the marker (0xE1 for APP1, 0xFE for a comment).
     */
    public static byte[] withJpegSegment(byte[] jpeg, int marker, byte[] payload) {
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + payload.length + 4);
        out.put(jpeg, 0, 2);
        out.put((byte) 0xFF).put((byte) marker).putShort((short) (payload.length + 2)).put(payload);
        out.put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    /** An EXIF APP1 payload with the Orientation tag, plus {@code secret} in the ImageDescription tag (like a GPS or owner note). */
    public static byte[] exifPayload(int orientation, String secret) {
        byte[] text = (secret + "\0").getBytes(StandardCharsets.US_ASCII);
        // TIFF (big endian): header 8 bytes, IFD0 with 2 entries (0x010E description, 0x0112 orientation), then the text.
        int textOffset = 8 + 2 + 2 * 12 + 4;
        ByteBuffer b = ByteBuffer.allocate(6 + textOffset + text.length);
        b.put("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        b.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        b.putShort((short) 2);
        b.putShort((short) 0x010E).putShort((short) 2).putInt(text.length).putInt(textOffset);
        b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        b.putInt(0);
        b.put(text);
        return b.array();
    }

    /** True when {@code haystack} contains {@code needle}. */
    public static boolean contains(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + n.length <= haystack.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * A PNG that only has the signature, an IHDR announcing {@code width} x {@code height} and IEND: a few dozen
     * bytes that would be gigabytes of pixels if anyone decoded them (the shape of a decompression bomb).
     */
    public static byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 2)
                .put((byte) 0).put((byte) 0).put((byte) 0);
        ByteBuffer out = ByteBuffer.allocate(8 + 25 + 12);
        out.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IEND", new byte[0]);
        return out.array();
    }

    private static void chunk(ByteBuffer out, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.putInt(data.length).put(name).put(data).putInt((int) crc.getValue());
    }
}
