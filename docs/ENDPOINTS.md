# Contratos HTTP de ai-labs

Rutas locales de ejemplo: auth escucha en `http://localhost:8080` según `README.md` y `auth/src/main/resources/application.properties`. En producción, el host se configura para escuchar en `0.0.0.0`; el puerto configurado es 8080.

## auth

### GET /q/health

**1. Qué hace**

Informa si el servicio de autenticación está disponible para que un orquestador o componente de operación decida si puede seguir enviándole tráfico.

**2. Autenticación**

No requiere autenticación ni header `Authorization` en la configuración comprobada. La extensión SmallRye Health no aplica seguridad por defecto; la guía de Quarkus 3.27 describe estos endpoints como checks para consumidores M2M. La aplicación no declara reglas de seguridad HTTP en su configuración actual.

**3. Request**

No tiene campos ni cuerpo. Método `GET` a `/q/health`.

**4. Response exitosa**

- Estado: `200 OK` cuando el estado global es `UP`.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `status` | string | Estado global; `UP` indica que los checks registrados están bien. |
| `checks` | array de objetos | Checks individuales. Cada elemento tiene `name` (string) y `status` (string `UP` o `DOWN`); `data` (objeto de valores string, boolean o number) es opcional. |

En el código de auth no hay procedimientos `HealthCheck` registrados. Según SmallRye Health/ MicroProfile Health 4.0, si no hay checks esperados o instalados, la respuesta es `UP` con una lista vacía. Ejemplo estándar aplicable a esa condición:

```json
{
  "status": "UP",
  "checks": []
}
```

La estructura y el mapping HTTP siguen MicroProfile Health 4.0, Appendix A/B. Las pruebas `@QuarkusTest` verifican HTTP 200 con `status: UP`; en el contexto de test se registra un readiness check auxiliar, por lo que la muestra de `checks: []` corresponde a la especificación para una ejecución sin checks y no es una captura del contexto de test. No se verificó un proceso externo en producción.

**5. Respuestas de error posibles**

- `503 Service Unavailable`: el contrato MicroProfile Health lo asigna al estado global `DOWN`. La prueba de auth registra un readiness check auxiliar y verifica este estado cuando dicho check informa DOWN. Ejemplo estándar para estado `DOWN` sin checks instalados:

```json
{
  "status": "DOWN",
  "checks": []
}
```

- `500 Internal Server Error`: la especificación lo reserva para un error al procesar la consulta del health check. MicroProfile Health especifica que no se devuelve payload JSON para este caso; por tanto, la respuesta documentada es sin cuerpo JSON. No hay un disparador de ese fallo del runtime en auth ni se ha probado; queda pendiente.

**6. Notas de seguridad**

El endpoint publica información operacional sin autenticación, comportamiento estándar de SmallRye Health y no alterado por auth. No añadir secretos, credenciales ni datos sensibles a checks personalizados.

Evidencia local: dependencia `auth/pom.xml:39-41`; configuración HTTP `auth/src/main/resources/application.properties:1-3`; no hay procedimientos `HealthCheck` en fuentes productivas `auth/src/main/java/`; tests HTTP en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:37-65`. Referencias: [guía SmallRye Health de Quarkus 3.27](https://quarkus.io/version/3.27/guides/smallrye-health) y [MicroProfile Health 4.0](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html). Proceso externo de producción no verificado.

### POST /auth/login

**1. Qué hace**

Autentica un usuario del workshop y, si las credenciales coinciden, devuelve su perfil y un token del flujo legacy.

**2. Autenticación**

La solicitud no requiere un token previo ni header `Authorization`; las credenciales se envían en el cuerpo JSON. Se requiere `Content-Type: application/json`. Base local: `http://localhost:8080/auth/login`.

**3. Request**

| Campo | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `username` | string | Sí | Nombre de usuario; no puede ser null, vacío ni contener solo espacios. |
| `password` | string | Sí | Contraseña; no puede ser null, vacía ni contener solo espacios. |
| `client` | string | No | Campo aceptado por el request, actualmente no utilizado por auth. |

Ejemplo completo con las credenciales seed documentadas para el workshop:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

**4. Response exitosa**

- Estado: `200 OK` cuando username y password autentican.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `token` | string | Token legacy codificado en Base64. |
| `expiresIn` | integer | Duración anunciada en segundos: `3600`. |
| `user` | object | Perfil del usuario autenticado. |
| `user.username` | string | Username del usuario. |
| `user.displayName` | string | Nombre visible escapado para HTML. |
| `user.role` | string | Rol almacenado para el usuario; las cuentas seed usan `STUDENT` y `TRAINER`. |
| `debug` | string | Marcador legacy fijo `authenticated-with-legacy-flow`. |

Ejemplo completo. El token de muestra se generó con el secreto exclusivamente ficticio de tests y un timestamp de ejemplo; no es una credencial de producción:

```json
{
  "token": "c3R1ZGVudHxTVFVERU5UfDE3OTMwNTQ5MzZ8VEVTVF9PTkxZX0FVVEhfVE9LRU5fU0VDUkVUX0NIQU5HRV9NRV8yMDI2",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

**5. Respuestas de error posibles**

- `400 Bad Request`, cuerpo JSON `null` (request nulo):

```json
{"message":"Request is null"}
```

- `400 Bad Request`, username ausente, null, vacío o solo espacios:

```json
{"message":"Username is required"}
```

- `400 Bad Request`, password ausente, null, vacío o solo espacios:

```json
{"message":"Password is required"}
```

- `401 Unauthorized`, username o password no coinciden:

```json
{"message":"Invalid user student or password"}
```

- `500 Internal Server Error`, una excepción que `AuthResource` captura mientras ejecuta `AuthService.authenticate`:

```json
{"message":"Internal server error"}
```

La capa REST/Jackson puede rechazar JSON malformado o campos con tipos incompatibles antes de invocar `login`; el cuerpo exacto de esas respuestas no está definido en `AuthResource` ni caracterizado por las pruebas actuales. **Pregunta abierta:** verificar el status y cuerpo exactos para JSON malformado, body vacío, tipo incorrecto, `Content-Type` no soportado y ruta/método no existentes.

**6. Notas de seguridad**

El password se compara contra BCrypt en persistencia y no se devuelve. Los errores de credenciales incluyen el username enviado, pero no el password. CORS permite por defecto `http://localhost:4200`; `AUTH_CORS_ORIGINS` permite configurar otros orígenes. El despliegue debe proporcionar `AUTH_TOKEN_SECRET`, que respalda `auth.token.secret` en `application.properties`.

El token actual **no está firmado ni se valida como credencial de acceso**: el código concatena username, role, timestamp y el secreto configurado, y codifica ese texto con Base64. `expiresIn: 3600` y el timestamp incluidos no implican que auth verifique la firma o haga cumplir la expiración; no hay endpoint de validación de tokens en auth. No usar este token como prueba de identidad hasta resolver H-AUTH-03 y la compatibilidad del formato legacy. El hallazgo H-AUTH-03 continúa pendiente en `HALLAZGOS.md`.

Evidencia local: ruta, validaciones y errores en `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:14-53`; campos request en `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-7`; DTO de salida en `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-5`; duración, mapeo y secreto en `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:9-32`; formato del token en `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:10-13`; CORS y secreto en `auth/src/main/resources/application.properties:1-9`; ejemplos probados en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:31-80`.

## Documentos consultados

- `README.md`
- `RETO.md`
- `AGENTS.md`
- `docs/GOOD_PRACTICES.md`
- `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java`
- `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`
- `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
- `auth/src/main/resources/application.properties`
- `auth/pom.xml`
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`
- Guía [SmallRye Health de Quarkus 3.27](https://quarkus.io/version/3.27/guides/smallrye-health)
- Especificación [MicroProfile Health 4.0](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html)
