package com.meuquarto.identity.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * The public origin of the site ({@code SITE_URL}), the only base for links sent by e-mail. It comes
 * from configuration, never from a request header, so a forged Host cannot point a reset link
 * somewhere else. Validated at startup: absolute http(s) origin, no credentials, path, query or
 * fragment, and plain http only for a local address.
 */
public final class SiteUrl {

    public static final String DEFAULT = "https://meuquarto.com.br";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final String origin;

    private SiteUrl(String origin) {
        this.origin = origin;
    }

    /** Blank means the production default. Throws {@link IllegalArgumentException} (stops startup) when invalid. */
    public static SiteUrl parse(String configured) {
        String text = configured == null || configured.isBlank() ? DEFAULT : configured.strip();
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            throw invalid();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean originOnly = uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"));
        boolean schemeAllowed = scheme.equals("https") || (scheme.equals("http") && LOCAL_HOSTS.contains(host));
        if (host.isEmpty() || !originOnly || !schemeAllowed) {
            throw invalid();
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return new SiteUrl(scheme + "://" + host + port);
    }

    /** The page where the new password is chosen. The token is the only variable part and is URL-safe. */
    public String resetLink(ResetToken token) {
        return origin + "/redefinir-senha?token=" + token.value();
    }

    private static IllegalArgumentException invalid() {
        // The configured value is not echoed: keep the message free of anything pasted by mistake.
        return new IllegalArgumentException(
                "SITE_URL must be an https origin such as https://meuquarto.com.br (http only for localhost), "
                        + "without credentials, path, query or fragment");
    }

    @Override
    public String toString() {
        return origin;
    }
}
