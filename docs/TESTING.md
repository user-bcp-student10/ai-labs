# Pruebas HTTP de ai-labs

## auth

Pruebas de integración HTTP con `@QuarkusTest`, REST Assured y SQLite local de auth. La suite está en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`. Los casos DB adicionales existentes también corren en la suite; la tabla siguiente enumera cobertura de los endpoints documentados en `docs/ENDPOINTS.md`.

| Endpoint | Caso | Tipo | Resultado esperado |
| --- | --- | --- | --- |
| `GET /q/health` | Check de readiness auxiliar reporta UP | Camino feliz | HTTP 200 y `status: UP`. |
| `GET /q/health` | Solicitud sin `Authorization` | Borde/seguridad | HTTP 200; el check auxiliar aparece en `checks`. |
| `GET /q/health` | Check de readiness auxiliar reporta DOWN | Error documentado | HTTP 503; `status: DOWN` y check con `status: DOWN`. |
| `GET /q/health` | Sin checks registrados, ejemplo `checks: []` | Pendiente | La especificación define HTTP 200 y lista vacía; no está probado porque el contexto QuarkusTest registra el check auxiliar. |
| `GET /q/health` | Fallo interno del procesamiento health | Pendiente | La especificación asigna HTTP 500 sin cuerpo JSON; no se ha podido inducir este fallo del runtime desde auth. |
| `POST /auth/login` | Credenciales seed válidas | Camino feliz | HTTP 200; `token`, `expiresIn`, `user` y `debug` con los valores/formatos documentados. |
| `POST /auth/login` | Request JSON `null` | Borde/error | HTTP 400, `{"message":"Request is null"}`. |
| `POST /auth/login` | Username ausente | Error | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | Username con solo espacios | Borde/error | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | Password ausente | Error | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | Password con solo espacios | Borde/error | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | Password incorrecto | Error | HTTP 401, `{"message":"Invalid user student or password"}`. |
| `POST /auth/login` | Excepción controlada del servicio | Error | HTTP 500, `{"message":"Internal server error"}`; sin detalle interno. |

## Código de las pruebas

Los métodos listados son código ejecutable de `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; no son pseudocódigo. La suite completa, incluidos los tests de SQL, CORS y migración de contraseña, permanece en ese archivo.

### Health

```java
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
```

El check controlado existe solo en test y permite verificar el mapeo HTTP de SmallRye Health; las fuentes productivas auth no registran un check de aplicación:

```java
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
```

### Login

```java
@Test
void logsInWithWorkshopUser() {
    String token = given()
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
            .extract().path("token");
    String tokenValue = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
    assertTrue(tokenValue.endsWith("|TEST_ONLY_AUTH_TOKEN_SECRET_CHANGE_ME_2026"));
}

@Test
void rejectsNullLoginRequest() {
    given().contentType("application/json").body("null")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Request is null"));
}

@Test
void rejectsMissingUsername() {
    given().contentType("application/json")
            .body("{\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Username is required"));
}

@Test
void rejectsWhitespaceUsername() {
    given().contentType("application/json")
            .body("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Username is required"));
}

@Test
void rejectsMissingPassword() {
    given().contentType("application/json")
            .body("{\"username\":\"student\"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Password is required"));
}

@Test
void rejectsWhitespacePassword() {
    given().contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"   \"}")
            .when().post("/auth/login")
            .then().statusCode(400).body("message", is("Password is required"));
}

@Test
void rejectsInvalidPassword() {
    given().contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"incorrect-test-password\"}")
            .when().post("/auth/login")
            .then().statusCode(401).body("message", is("Invalid user student or password"));
}

@Test
void returnsGenericServerErrorOverHttpWhenAuthenticationFails() {
    QuarkusMock.installMockForType(new AuthService() {
        @Override
        public LoginResponse authenticate(String username, String password) {
            throw new IllegalStateException("controlled HTTP test failure");
        }
    }, AuthService.class);

    given().contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"test-only-password\"}")
            .when().post("/auth/login")
            .then().statusCode(500).body("message", is("Internal server error"));
}
```

## Ejecuciones y cobertura

Todos los comandos Maven que siguen deben ejecutarse con directorio `auth/` desde la raíz `ai-labs/`.

| Comando | Directorio | Código de salida | Resultado |
| --- | --- | --- | --- |
| `mvn -Dtest=AuthResourceTest test` | `ai-labs/` (intento inicial, directorio incorrecto) | 1 | No corrió tests: Maven reportó que no hay POM en `ai-labs/`. Se repitió correctamente desde `auth/`. |
| `mvn -Dtest=AuthResourceTest test` | `auth/` | 0 | BUILD SUCCESS; 15 tests, 0 fallos, 0 errores, 0 omitidos. Incluye los casos nuevos de health y login. |
| `mvn verify` | `auth/` | 0 | BUILD SUCCESS; 15 tests, 0 fallos, 0 errores, 0 omitidos. JaCoCo generó el reporte. |

Medición de cobertura: se leyó `auth/target/site/jacoco/jacoco.xml` después de `mvn verify` y se sumaron contadores de líneas cubiertas/no cubiertas para las clases productivas auth afectadas (`AuthResource`, `AuthService`, `LoginResponse`, `LegacyDatabase`, `LegacyTokenUtil`). Resultado: **85/92 líneas = 92.4%**, supera la meta local de 85%. No es cobertura global del repositorio.

## Fallos

- El primer `mvn -Dtest=AuthResourceTest test` salió con código 1 porque se ejecutó desde `ai-labs/`, que no tiene `pom.xml`. La repetición desde `auth/` pasó; no fue un fallo de aplicación ni de test.

## Pendientes

- `GET /q/health` 500 sin cuerpo: no hay en auth un modo comprobado de causar un fallo del procesamiento HTTP del endpoint. La excepción de un `HealthCheck` no es equivalente a este caso; la especificación la convierte en un resultado de check DOWN. No se afirma cobertura de 500.
- Health UP con `checks: []`: la prueba de integración registra un check auxiliar, por lo que no confirma la respuesta vacía de una ejecución sin checks.
- JSON malformado, body vacío, tipos incompatibles, `Content-Type` no soportado y ruta/método inexistentes: `docs/ENDPOINTS.md` los deja como pregunta abierta porque el status/cuerpo no están acordados ni caracterizados.

## Pruebas propuestas

- Añadir un test de configuración/runtime separado que ejecute health sin `ControlledTestHealthCheck` para confirmar `200`, `UP` y `checks: []` en el perfil productivo.
- Acordar una forma reproducible de provocar un error de procesamiento de `/q/health` (sin confundirlo con un check que reporta DOWN) antes de escribir la prueba de `500`.
- Cuando se acuerde el contrato RESTEasy/Jackson para entrada malformada o unsupported media type, caracterizar cada código y cuerpo antes de convertirlo en expectativa de regresión.

## Documentos consultados

- `docs/ENDPOINTS.md`
- `README.md`
- `RETO.md`
- `AGENTS.md`
- `docs/GOOD_PRACTICES.md`
- `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java`
- `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`
- `auth/pom.xml`
- Guía SmallRye Health de Quarkus 3.27
- MicroProfile Health 4.0
