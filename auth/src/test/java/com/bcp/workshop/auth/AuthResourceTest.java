package com.bcp.workshop.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.Readiness;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import jakarta.ws.rs.core.Response;

@QuarkusTest
class AuthResourceTest {
    @Inject
    LegacyDatabase database;

    @ConfigProperty(name = "auth.token.secret")
    String tokenSecret;

    @Test
    void healthEndpointReportsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void healthEndpointDoesNotRequireAuthorization() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("checks[0].name", is("controlled-test-health"));
    }

    @Test
    void healthEndpointReturns503WhenAReadinessCheckIsDown() {
        ControlledTestHealthCheck.reportDown = true;
        try {
            given()
                    .when().get("/q/health")
                    .then()
                    .statusCode(503)
                    .body("status", is("DOWN"))
                    .body("checks[0].status", is("DOWN"));
        } finally {
            ControlledTestHealthCheck.reportDown = false;
        }
    }

    @Test
    void logsInWithWorkshopUser() {
        var response = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("user.displayName", notNullValue())
                .body("user.role", notNullValue())
                .body("debug", is("authenticated-with-legacy-flow"))
            .extract().response();
        String token = response.path("token");
        String role = response.path("user.role");

        assertEquals("HS256", TestJwt.header(token).get("alg"));
        assertTrue(TestJwt.hasValidHmacSignature(token, tokenSecret));
        assertEquals("student", TestJwt.payload(token).get("sub"));
        assertEquals(role, TestJwt.payload(token).get("role"));
        assertFalse(token.contains(tokenSecret));
        assertExpiresWithinConfiguredLifetime(TestJwt.payload(token));
    }

    private static void assertExpiresWithinConfiguredLifetime(Map<String, Object> payload) {
        long expiry = ((Number) payload.get("exp")).longValue();
        long now = Instant.now().getEpochSecond();
        assertTrue(expiry > now + 3599 && expiry <= now + 3600, "exp must be about one hour ahead");
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsNullLoginRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsWhitespaceUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsWhitespacePassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"   \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect-test-password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void findsCredentialsContainingQuoteAsLiteralData() throws Exception {
        String username = "quote'name-" + UUID.randomUUID();
        String password = "local-test-password";
        String databaseUrl = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
                insert.setString(1, username);
                insert.setString(2, BcryptUtil.bcryptHash(password));
                insert.setString(3, "Quote Test User");
                insert.setString(4, "TEST");
                insert.executeUpdate();
            }

            try {
                UserRecord user = database.findByCredentials(username, password);
                assertNotNull(user);
                assertEquals(username, user.username());
            } finally {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM legacy_users WHERE username=?")) {
                    delete.setString(1, username);
                    delete.executeUpdate();
                }
            }
        }
    }

    @Test
    void hidesExceptionDetailsInLoginErrorResponse() {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public LoginResponse authenticate(String username, String password) {
                throw new IllegalStateException("controlled test failure");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "test-only-password";

        Response response = resource.login(request);
        Map<?, ?> body = (Map<?, ?>) response.getEntity();

        assertEquals(500, response.getStatus());
        assertEquals("Internal server error", body.get("message"));
        org.junit.jupiter.api.Assertions.assertFalse(body.containsKey("exception"));
        org.junit.jupiter.api.Assertions.assertFalse(body.containsValue("controlled test failure"));
    }

    @Test
    void returnsGenericServerErrorOverHttpWhenAuthenticationFails() {
        QuarkusMock.installMockForType(new AuthService() {
            @Override
            public LoginResponse authenticate(String username, String password) {
                throw new IllegalStateException("controlled HTTP test failure");
            }
        }, AuthService.class);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"test-only-password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .body("message", is("Internal server error"));
    }

    @Test
    void rejectsUnconfiguredCorsOrigin() {
        String origin = "https://unapproved.example";

        given()
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void migratesLegacyPlaintextPasswordIdempotently() throws Exception {
        String username = "legacy-" + UUID.randomUUID();
        String password = "migration-test-password";
        String databaseUrl = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            insert.setString(1, username);
            insert.setString(2, password);
            insert.setString(3, "Legacy Test User");
            insert.setString(4, "TEST");
            insert.executeUpdate();
        }

        try {
            database.initialize();
            String firstHash = findStoredPassword(databaseUrl, username);
            assertNotEquals(password, firstHash);
            assertTrue(BcryptUtil.matches(password, firstHash));

            database.initialize();
            assertEquals(firstHash, findStoredPassword(databaseUrl, username));
        } finally {
            try (Connection connection = DriverManager.getConnection(databaseUrl);
                 PreparedStatement delete = connection.prepareStatement(
                         "DELETE FROM legacy_users WHERE username=?")) {
                delete.setString(1, username);
                delete.executeUpdate();
            }
        }
    }

    private String findStoredPassword(String databaseUrl, String username) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement query = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username=?")) {
            query.setString(1, username);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                return result.getString("password");
            }
        }
    }
}

@Readiness
@ApplicationScoped
class ControlledTestHealthCheck implements HealthCheck {
    static volatile boolean reportDown;

    @Override
    public HealthCheckResponse call() {
        return reportDown
                ? HealthCheckResponse.down("controlled-test-health")
                : HealthCheckResponse.up("controlled-test-health");
    }
}
