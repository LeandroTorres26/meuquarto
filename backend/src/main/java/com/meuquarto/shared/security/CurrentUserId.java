package com.meuquarto.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Injects the id of the signed-in user into a controller parameter ({@code long}). The owner of any
 * data always comes from here, never from the request. The id is read from the session principal by
 * expression, so a controller outside {@code identity} does not import the identity module.
 * Endpoints using it must require authentication (the default for every route).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "userId()")
public @interface CurrentUserId {}
