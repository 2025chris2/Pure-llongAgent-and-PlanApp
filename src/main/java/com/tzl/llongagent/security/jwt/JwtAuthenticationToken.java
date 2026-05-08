package com.tzl.llongagent.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String userId;
    private final String token;

    public JwtAuthenticationToken(String token) {
        super(null);
        this.token = token;
        this.userId = null;
        setAuthenticated(false);
    }

    public JwtAuthenticationToken(String userId, String token,
                                   Collection<SimpleGrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    public String getUserId() {
        return userId;
    }
}
