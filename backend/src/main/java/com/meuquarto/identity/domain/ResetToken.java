package com.meuquarto.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * A password reset token (ADR 0012): 256 random bits from {@link SecureRandom}, written URL-safe
 * (43 characters). Only {@link #hash()} is ever stored; {@link #value()} goes into the e-mailed link
 * and nowhere else. {@link #toString()} never shows the value, so it cannot reach a log by accident.
 */
public final class ResetToken {

    public static final int BYTES = 32;
    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final String value;

    private ResetToken(String value) {
        this.value = value;
    }

    public static ResetToken generate(SecureRandom random) {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return new ResetToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** The token typed back by a client, or null when it cannot be one of ours (wrong length or characters). */
    public static ResetToken parse(String candidate) {
        return candidate != null && SHAPE.matcher(candidate).matches() ? new ResetToken(candidate) : null;
    }

    public String value() {
        return value;
    }

    /** SHA-256 of the token, as 64 lowercase hex characters: what the database stores and looks up. */
    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    @Override
    public String toString() {
        return "ResetToken[hidden]";
    }
}
