package com.meuquarto.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Sends a CSRF token the way the web app does: read the XSRF-TOKEN cookie from GET /api/auth/csrf
 * and send it back as cookie and X-XSRF-TOKEN header.
 * Use this instead of spring-security-test's {@code csrf()}, which swaps the token repository inside
 * the shared CsrfFilter and leaks into later tests that run in the same Spring context.
 */
public final class RealCsrf {

    private RealCsrf() {}

    public static RequestPostProcessor token(MockMvc mvc) throws Exception {
        Cookie xsrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (xsrf == null) {
            throw new IllegalStateException("GET /api/auth/csrf did not set the XSRF-TOKEN cookie");
        }
        return request -> {
            List<Cookie> cookies = new ArrayList<>(request.getCookies() == null ? List.of() : Arrays.asList(request.getCookies()));
            cookies.add(xsrf);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", xsrf.getValue());
            return request;
        };
    }
}
