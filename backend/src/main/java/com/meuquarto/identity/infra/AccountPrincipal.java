package com.meuquarto.identity.infra;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The signed-in user as Spring Security sees it; stored in the session (no password after login). */
public class AccountPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String email;
    private String passwordHash;

    public AccountPrincipal(long userId, String email, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public long userId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
