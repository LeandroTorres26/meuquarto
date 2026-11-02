package com.meuquarto.profile.domain;

import java.awt.geom.AffineTransform;

/**
 * The EXIF "Orientation" tag of a JPEG (1 to 8): phones store photos unrotated and record here how to
 * turn them. Re-encoding drops all metadata, so the turn is applied to the pixels first. The reader is
 * deliberately small and strict: anything it does not understand means "no rotation".
 */
final class ExifOrientation {

    private static final int NORMAL = 1;

    private ExifOrientation() {}

    /** Orientation from the first APP1/Exif segment of a JPEG, or 1 when there is none or it is malformed. */
    static int of(byte[] jpeg) {
        int pos = 2; // after the SOI marker
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) { // start of scan or end of image: no more metadata
                break;
            }
            int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if (length < 2 || pos + 2 + length > jpeg.length) {
                break;
            }
            if (marker == 0xE1 && isExif(jpeg, pos + 4, pos + 2 + length)) {
                return fromTiff(jpeg, pos + 10, pos + 2 + length);
            }
            pos += 2 + length;
        }
        return NORMAL;
    }

    private static boolean isExif(byte[] b, int from, int end) {
        return end - from >= 6 && b[from] == 'E' && b[from + 1] == 'x' && b[from + 2] == 'i' && b[from + 3] == 'f'
                && b[from + 4] == 0 && b[from + 5] == 0;
    }

    /** Reads IFD0 of the TIFF structure that starts at {@code tiff} and ends before {@code end}. */
    private static int fromTiff(byte[] b, int tiff, int end) {
        if (end - tiff < 8) {
            return NORMAL;
        }
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        boolean big = b[tiff] == 'M' && b[tiff + 1] == 'M';
        if (!little && !big) {
            return NORMAL;
        }
        long ifd = unsigned(b, tiff + 4, 4, little);
        if (ifd < 8 || tiff + ifd + 2 > end) {
            return NORMAL;
        }
        int entries = (int) unsigned(b, tiff + (int) ifd, 2, little);
        int entry = tiff + (int) ifd + 2;
        for (int i = 0; i < entries && entry + 12 <= end; i++, entry += 12) {
            if (unsigned(b, entry, 2, little) == 0x0112) {
                long value = unsigned(b, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? (int) value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static long unsigned(byte[] b, int at, int size, boolean little) {
        long value = 0;
        for (int i = 0; i < size; i++) {
            int octet = b[little ? at + size - 1 - i : at + i] & 0xFF;
            value = (value << 8) | octet;
        }
        return value;
    }

    /** The transform that turns a {@code size} x {@code size} picture stored with this orientation upright. */
    static AffineTransform upright(int orientation, int size) {
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(size, 0); t.scale(-1, 1); }                       // mirrored horizontally
            case 3 -> { t.translate(size, size); t.rotate(Math.PI); }                 // upside down
            case 4 -> { t.translate(0, size); t.scale(1, -1); }                       // mirrored vertically
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }                     // transposed
            case 6 -> { t.translate(size, 0); t.rotate(Math.PI / 2); }               // turned 90 degrees clockwise
            case 7 -> { t.translate(size, size); t.rotate(Math.PI / 2); t.scale(-1, 1); } // transverse
            case 8 -> { t.translate(0, size); t.rotate(-Math.PI / 2); }              // turned 90 degrees counter-clockwise
            default -> { }                                                            // 1: already upright
        }
        return t;
    }
}
