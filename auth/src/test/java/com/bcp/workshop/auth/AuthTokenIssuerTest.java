package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenIssuerTest {
    private static final String TEST_SECRET = "UNIT_TEST_ONLY_SECRET_AT_LEAST_32_BYTES_LONG";
    private static final String OTHER_SECRET = "UNIT_TEST_ONLY_OTHER_SECRET_AT_LEAST_32_BYTES";

    private AuthTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new AuthTokenIssuer();
        issuer.tokenSecret = TEST_SECRET;
    }

    @Test
    void issuesHs256TokenSignedWithConfiguredSecret() {
        String token = issuer.issue("student", "STUDENT");

        assertEquals(3, token.split("\\.").length);
        assertEquals("HS256", TestJwt.header(token).get("alg"));
        assertTrue(TestJwt.hasValidHmacSignature(token, TEST_SECRET));
    }

    @Test
    void carriesSubjectRoleAndExpirationClaims() {
        String token = issuer.issue("student", "STUDENT");
        Map<String, Object> payload = TestJwt.payload(token);

        assertEquals("student", payload.get("sub"));
        assertEquals("STUDENT", payload.get(AuthTokenIssuer.ROLE_CLAIM));
        long expiry = ((Number) payload.get("exp")).longValue();
        long now = Instant.now().getEpochSecond();
        assertTrue(expiry > now + AuthTokenIssuer.TOKEN_EXPIRATION_SECONDS - 60);
        assertTrue(expiry <= now + AuthTokenIssuer.TOKEN_EXPIRATION_SECONDS);
    }

    @Test
    void doesNotExposeSecretInToken() {
        String token = issuer.issue("student", "STUDENT");

        assertFalse(token.contains(TEST_SECRET));
    }

    @Test
    void rejectsTokenWhenPayloadIsTampered() {
        String token = issuer.issue("student", "STUDENT");
        Map<String, Object> forgedPayload = new HashMap<>(TestJwt.payload(token));
        forgedPayload.put(AuthTokenIssuer.ROLE_CLAIM, "ADMIN");

        String tampered = TestJwt.withPayload(token, forgedPayload);

        assertFalse(TestJwt.hasValidHmacSignature(tampered, TEST_SECRET));
    }

    @Test
    void rejectsTokenVerifiedWithDifferentSecret() {
        String token = issuer.issue("student", "STUDENT");

        assertFalse(TestJwt.hasValidHmacSignature(token, OTHER_SECRET));
    }
}
