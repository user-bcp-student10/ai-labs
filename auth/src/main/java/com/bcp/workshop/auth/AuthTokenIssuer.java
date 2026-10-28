package com.bcp.workshop.auth;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AuthTokenIssuer {
    static final int TOKEN_EXPIRATION_SECONDS = 3600;
    static final String ROLE_CLAIM = "role";

    @ConfigProperty(name = "auth.token.secret")
    String tokenSecret;

    public String issue(String username, String role) {
        return Jwt.subject(username)
                .claim(ROLE_CLAIM, role)
                .expiresIn(TOKEN_EXPIRATION_SECONDS)
                .jws()
                .algorithm(SignatureAlgorithm.HS256)
                .signWithSecret(tokenSecret);
    }
}
