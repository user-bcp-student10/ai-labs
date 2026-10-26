package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AuthService {
    private static final int TOKEN_EXPIRATION_SECONDS = 3600;
    private static final String LEGACY_FLOW_DEBUG_VALUE = "authenticated-with-legacy-flow";

    @Inject
    LegacyDatabase database;

    @ConfigProperty(name = "auth.token.secret")
    String tokenSecret;

    public LoginResponse authenticate(String username, String password) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            return null;
        }

        String token = LegacyTokenUtil.create(user.username(), user.role(), tokenSecret);
        LoginResponse.UserDetails userDetails = new LoginResponse.UserDetails(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role()
        );
        return new LoginResponse(token, TOKEN_EXPIRATION_SECONDS, userDetails, LEGACY_FLOW_DEBUG_VALUE);
    }
}