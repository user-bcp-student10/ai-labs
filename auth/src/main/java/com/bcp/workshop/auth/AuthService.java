package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

@ApplicationScoped
public class AuthService {
    private static final String LEGACY_FLOW_DEBUG_VALUE = "authenticated-with-legacy-flow";

    @Inject
    LegacyDatabase database;

    @Inject
    AuthTokenIssuer tokenIssuer;

    public LoginResponse authenticate(String username, String password) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            return null;
        }

        String token = tokenIssuer.issue(user.username(), user.role());
        LoginResponse.UserDetails userDetails = new LoginResponse.UserDetails(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role()
        );
        return new LoginResponse(token, AuthTokenIssuer.TOKEN_EXPIRATION_SECONDS, userDetails, LEGACY_FLOW_DEBUG_VALUE);
    }
}