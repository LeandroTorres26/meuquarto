package com.meuquarto.profile.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Public address of a profile, as in {@code meuquarto.com.br/<slug>} (BR-01).
 * Input is trimmed and lowercased; the result must be 3 to 30 characters of {@code a-z}, {@code 0-9}
 * and single hyphens between them, and must not be a reserved word.
 */
public record Slug(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern CHARACTERS = Pattern.compile("[a-z0-9-]*");

    /** Routes and names a profile must never shadow. */
    static final Set<String> RESERVED = Set.of(
            // app routes
            "api", "painel", "entrar", "sair", "cadastro", "esqueci-senha", "redefinir-senha",
            "termos", "privacidade", "loja", "compras", "conta", "quarto", "item", "obra", "busca",
            // framework and infrastructure paths
            "static", "assets", "public", "favicon", "robots", "sitemap", "manifest", "health", "status",
            // identity and support
            "meuquarto", "admin", "administrador", "root", "suporte", "ajuda", "sobre", "contato",
            "login", "logout", "signup", "signin", "register", "settings", "config", "dashboard",
            "www", "mail", "email", "blog", "app", "null", "undefined");

    public Slug {
        Problem problem = check(value).orElse(null);
        if (problem != null) {
            throw new InvalidSlugException(problem);
        }
    }

    /** Normalizes raw input (trim, lowercase) and validates it. */
    public static Slug of(String raw) {
        return new Slug(normalize(raw));
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** Why an already normalized value is not a valid slug, if it isn't. */
    public static Optional<Problem> check(String normalized) {
        if (normalized == null || normalized.length() < MIN_LENGTH) {
            return Optional.of(Problem.TOO_SHORT);
        }
        if (normalized.length() > MAX_LENGTH) {
            return Optional.of(Problem.TOO_LONG);
        }
        if (!CHARACTERS.matcher(normalized).matches()) {
            return Optional.of(Problem.INVALID_CHARACTERS);
        }
        if (!FORMAT.matcher(normalized).matches()) {
            return Optional.of(Problem.INVALID_HYPHEN);
        }
        if (RESERVED.contains(normalized)) {
            return Optional.of(Problem.RESERVED);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }

    public enum Problem {
        TOO_SHORT,
        TOO_LONG,
        /** Anything other than a-z, 0-9 and hyphen, including accents and spaces. */
        INVALID_CHARACTERS,
        /** Hyphen at the start or end, or two in a row. */
        INVALID_HYPHEN,
        RESERVED
    }
}
