package com.meuquarto.profile.domain;

import java.util.Optional;

/**
 * The image formats an avatar may be uploaded in, recognised by the first bytes of the content
 * (magic bytes). The file name and the {@code Content-Type} sent by the client are never looked at.
 * WebP is not here on purpose: the JDK has no WebP decoder, and a file we cannot decode cannot be
 * re-encoded safely.
 */
public enum ImageType {
    JPEG("jpeg", new int[] {0xFF, 0xD8, 0xFF}),
    PNG("png", new int[] {0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String readerFormat;
    private final int[] signature;

    ImageType(String readerFormat, int[] signature) {
        this.readerFormat = readerFormat;
        this.signature = signature;
    }

    /** Name of the ImageIO reader for this format. */
    String readerFormat() {
        return readerFormat;
    }

    /** The type whose signature the content starts with, if any. */
    public static Optional<ImageType> detect(byte[] content) {
        for (ImageType type : values()) {
            if (type.matches(content)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private boolean matches(byte[] content) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
