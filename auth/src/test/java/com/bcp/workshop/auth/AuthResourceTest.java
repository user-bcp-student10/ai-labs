package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String INJECTION_PAYLOAD = "' OR '1'='1";

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    // Spec auth-login: credenciales normales no válidas responden 401 sin token.
    @Test
    void rejectsInvalidCredentialsWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong-password\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    // Spec auth-login, escenario "Entrada de inyección en el usuario".
    @Test
    void rejectsInjectionInUsernameWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"" + INJECTION_PAYLOAD + "\",\"password\":\"wrong-password\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    // Spec auth-login, escenario "Entrada de inyección en la contraseña".
    @Test
    void rejectsInjectionInPasswordWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"" + INJECTION_PAYLOAD + "\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }
}
