# Plan de auth

## Alcance

Propuesta para los siete hallazgos pendientes de auth. La selección de IDs cuenta con aprobación humana según el registro al final de este archivo. La aprobación habilita únicamente los cambios y condiciones descritos aquí.

`docs/ENDPOINTS.md` no existe. El comportamiento de referencia observado en `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java` es `POST /auth/login`: responde 400 si falta el request, username o password; 401 si las credenciales no coinciden; y 200 con `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug` si autentica. `LoginRequest.client` se acepta pero no se usa. El 500 actual expone mensaje y clase de excepción. Genericizarlo es un cambio de seguridad intencional. No consta contrato documentado para consumidores del formato interno del token.

## IDs seleccionados

| ID | Motivo de selección |
|---|---|
| H-AUTH-01 | Alta: eliminar concatenación SQL de credenciales. |
| H-AUTH-02 | Alta: dejar de almacenar contraseñas recuperables en claro. |
| H-AUTH-03 | Alta: sustituir token Base64 sin integridad por token firmado. |
| H-AUTH-04 | Media: limitar exposición de detalles internos en errores. |
| H-AUTH-05 | Media: externalizar el secreto y restringir CORS. |
| H-AUTH-06 | Media: investigar advisories de versiones presentes. El análisis confirmó CVE-2022-42889 en Commons Text 1.9; se aplicó 1.10.0 y el reescaneo de las coordenadas evaluadas quedó limpio. |
| H-AUTH-07 | Baja: reducir acoplamiento y separar responsabilidades. |

## Propuesta por hallazgo

### H-AUTH-01: SQL de credenciales concatenada

**Problema:** `LegacyDatabase.findByCredentials` inserta username y password dentro del texto SQL, por lo que las comillas pueden cambiar la sintaxis o el significado de la consulta. El recurso entrega ambos campos al método de persistencia.

**Cambio propuesto:** usar `PreparedStatement` y enlazar con `setString` cada entrada que participa en SQL. Al hashear contraseñas en H-AUTH-02, consultar por `username=?` y verificar el password presentado con BCrypt fuera de SQL; no registrar credenciales ni SQL con valores.

**Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; pruebas existentes o nuevas bajo `auth/src/test/java/com/bcp/workshop/auth/`.

**Caracterización antes del cambio:** insertar por JDBC parametrizado una fila ficticia cuyo username contiene una comilla (por ejemplo, `quote'name`) y comprobar que la búsqueda falla con la consulta concatenada actual; comprobar también el login válido actual. La prueba debe demostrar que discrimina entre concatenación y parámetros enlazados.

**Comprobación después:** la búsqueda devuelve exactamente esa fila literal; el login válido sigue en 200 y las credenciales inválidas siguen en 401.

### H-AUTH-02: Contraseñas en texto plano

**Problema:** el esquema y las filas semilla almacenan contraseñas legibles; una exposición de SQLite revela directamente las credenciales.

**Cambio propuesto:** hash lento con salt (BCrypt, según `docs/GOOD_PRACTICES.md`), verificación de hash durante el login y una migración idempotente/transaccional definida para datos SQLite existentes. No conservar una comparación plaintext como fallback indefinido. Justificar cualquier dependencia antes de añadirla.

**Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; una clase de autenticación/servicio si el diseño lo requiere; `auth/pom.xml` solo si se justifica una dependencia; pruebas en `auth/src/test/java/com/bcp/workshop/auth/`.

**Caracterización antes del cambio:** probar login válido con credenciales ficticias y el comportamiento de las cuentas semilla; registrar el estado de datos existente sin incluir secretos reales.

**Comprobación después:** login válido e inválido; el valor persistido no es la contraseña original; migración repetible sin perder usuarios seed ni romper el login. Usar exclusivamente valores de prueba.

**Revisión local:** H-AUTH-02 queda `Pendiente`. El detector actual considera hash cualquier valor con prefijo `$2a$`, `$2b$` o `$2y$`, y el `@PostConstruct` absorbe errores de migración; faltan pruebas/corrección para asegurar que una contraseña plaintext coincidente con esos prefijos no quede almacenada y que un fallo de migración impida continuar como si hubiera terminado correctamente.

### H-AUTH-03: Token Base64 sin firma

**Problema:** el token concatena username, rol, expiración y secreto, y luego los codifica con Base64, que no proporciona integridad ni firma verificable.

**Cambio propuesto:** emitir token firmado, con expiración y claims explícitos, mediante una solución mantenible; no implementar Base64 casero. Mantener el campo JSON `token`, `expiresIn` y el resto de la respuesta observada. No asumir compatibilidad del formato interno legacy: requiere decisión humana si algún consumidor lo interpreta.

**Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`, `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`, configuración de auth y `auth/pom.xml` solo si la dependencia elegida se justifica; pruebas en `auth/src/test/java/com/bcp/workshop/auth/`.

**Caracterización antes del cambio:** comprobar la respuesta 200 y la presencia/tipo de `token`, `expiresIn` y los campos actuales de `user`.

**Comprobación después:** verificar con la biblioteca elegida la firma, claims acordados y expiración del token generado; conservar la estructura HTTP. No se presupone que exista un endpoint de validación de tokens en auth. La clave de test será ficticia.

**Cambio aplicado:** se añadió `quarkus-smallrye-jwt-build` (versión gestionada por el BOM de Quarkus 3.27.4). `AuthTokenIssuer` emite JWT HS256 con `sub`, `role` y `exp` (3600 s, constante compartida con `expiresIn`), firmado con `auth.token.secret`. `AuthService` delega en el emisor; `LegacyTokenUtil.java` se eliminó. Decisiones aprobadas: el token legacy se rechaza en despliegue (no hay compatibilidad) y el campo `debug` no cambia. `AUTH_TOKEN_SECRET` debe tener al menos 32 bytes para HS256; rotar el secreto invalida los tokens emitidos.

**Comprobación real:** `python tools/lab.py test auth` antes del cambio: BUILD SUCCESS, 15 tests. Después: BUILD SUCCESS, 20 tests (15 en `AuthResourceTest`, 5 nuevos en `AuthTokenIssuerTest`: HS256 con firma verificada con JCA, claims `sub`/`role`/`exp`, secreto ausente del token, rechazo de payload alterado y de otra clave). JaCoCo: `AuthTokenIssuer` 7/7 líneas, `AuthService` 10/10 líneas.

### H-AUTH-04: Detalles de excepción expuestos

**Problema:** el catch del recurso devuelve al cliente el mensaje y el nombre de clase de la excepción.

**Cambio propuesto:** responder con mensaje genérico y registrar detalles solo mediante logging controlado, nunca credenciales ni secretos.

**Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java` y `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.

**Caracterización antes del cambio:** mediante un fallo controlado/test double, observar status y cuerpo 500 actuales.

**Comprobación después:** conservar status 500, verificar mensaje genérico y asegurar que ni el mensaje interno ni el nombre de la clase aparezcan en la respuesta.

### H-AUTH-05: Secreto hardcodeado y CORS permisivo

**Problema:** el secreto está fijado en código y CORS usa una expresión wildcard que admite cualquier origen.

**Cambio propuesto:** leer el secreto desde configuración respaldada por variable de entorno, con validación de configuración y valores ficticios en tests. Restringir CORS sin wildcard; usar `http://localhost:4200` como origen local observado en README y permitir otros orígenes solo mediante configuración aprobada.

**Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`, `auth/src/main/resources/application.properties` y pruebas de configuración bajo `auth/src/test/java/com/bcp/workshop/auth/`.

**Caracterización antes del cambio:** capturar la configuración CORS actual y el uso del secreto por el token.

**Comprobación después:** probar origen permitido y rechazado y comprobar que el secreto no está en código ni es necesario como valor real. El comportamiento CORS debe corresponder a la lista configurada.

### H-AUTH-06: Evaluación SCA y parche

**Problema:** el análisis confirmó CVE-2022-42889 (crítico) en la dependencia directa Commons Text 1.9.

**Cambio aplicado:** actualizar Commons Text a 1.10.0, versión recomendada por el advisory. No se actualizó Quarkus ni otras dependencias.

**Archivos previstos:** `auth/pom.xml` solo si un advisory confirmado justifica un cambio; las evidencias se registran aquí y en HALLAZGOS.md.

**Caracterización antes del cambio:** el validador CVE de Java reportó CVE-2022-42889 en `org.apache.commons:commons-text:1.9`, severidad crítica y versión corregida 1.10.0, entre las coordenadas declaradas/gestionadas evaluadas.

**Comprobación después:** el mismo validador no reportó CVE conocido para las seis coordenadas evaluadas con `commons-text:1.10.0` (cuatro extensiones Quarkus 3.27.4, Commons Text y SQLite JDBC 3.50.3.0). `mvn verify` posterior: BUILD SUCCESS, 8 tests aprobados. El chequeo cubrió esas coordenadas, no constituye un análisis de todo el grafo resuelto. CodeQL y Dependency Review remotos siguen no ejecutados.

### H-AUTH-07: Responsabilidades mezcladas en AuthResource

**Problema:** `login` valida la entrada, consulta persistencia, genera token y arma la respuesta; esto acopla HTTP, negocio y datos.

**Cambio aplicado:** `AuthResource` conserva validación/status HTTP; `AuthService` concentra autenticación, token y mapeo; `LegacyDatabase` continúa como repositorio SQLite; `LoginResponse` y `UserDetails` son DTOs, sin devolver `UserRecord`. Se usan guard clauses y constantes con nombre. El formato legacy del token se preservó porque H-AUTH-03 sigue bloqueado por compatibilidad.

**Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`, `LegacyDatabase.java`, tipos nuevos de servicio/DTO si son necesarios y pruebas bajo `auth/src/test/java/com/bcp/workshop/auth/`.

**Caracterización antes del cambio:** `mvn -Dtest=AuthResourceTest test` aprobó 8 tests con status y estructura del login válido, 400 por username/password requeridos y 401 inválido antes de refactorizar producción.

**Comprobación después:** la misma clase aprobó 8 tests tras el cambio; `mvn verify` aprobó los 8 tests. JaCoCo midió 84/92 líneas (91.3%) agregadas en `AuthResource`, `AuthService`, `LoginResponse`, `LegacyDatabase` y `LegacyTokenUtil`, por encima de la meta de 85%.

## Secuencia y dependencias

1. Caracterización común de POST `/auth/login` antes de cambios: 200 válido, 400 de validación, 401 inválido y respuesta 500 mediante fallo controlado.
2. H-AUTH-01, SQL parametrizada.
3. H-AUTH-04, error genérico.
4. H-AUTH-05, configuración de secreto y CORS.
5. H-AUTH-02, hashing y migración; caracterizar datos antes de migrar.
6. H-AUTH-03, token firmado; depende de configuración de H-AUTH-05 y de resolver compatibilidad del token legacy.
7. H-AUTH-06, evaluación SCA; cualquier actualización depende de advisory aplicable confirmado.
8. H-AUTH-07, separación de capas al final, consolidando las responsabilidades sin cambiar el contrato aprobado.
9. Regresión, revisión de diff y cobertura del código modificado.

## Aprobación y bloqueos


- Contrato de endpoints: no hay `docs/ENDPOINTS.md`; se usa el comportamiento observado descrito al inicio.
- Compatibilidad del token legacy: resuelta por decisión humana; el token legacy se rechaza en despliegue y no se mantiene compatibilidad.
- H-AUTH-03: resuelto con token JWT HS256 (SmallRye JWT); ver ejecuciones abajo.
- CodeQL y Dependency Review remotos no fueron ejecutados. El SCA local cubrió únicamente las seis coordenadas indicadas en el registro.
- `auth/pom.xml` figuraba modificado previamente; inspeccionar su diff desde la raíz y preservar cambios ajenos antes de cualquier edición.

## Registro de ejecuciones

| Fecha | Componente | IDs | Comando | Resultado |
|---|---|---|---|---|
| 2026-10-26 | auth | H-AUTH-01 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#findsCredentialsContainingQuoteAsLiteralData test` (antes del cambio) | 1 test, falló como se esperaba: la consulta concatenada devolvió `null` para username con comilla. |
| 2026-10-26 | auth | H-AUTH-01 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#findsCredentialsContainingQuoteAsLiteralData test` (después del cambio) | 1 test aprobado; se recuperó el usuario literal. |
| 2026-10-26 | auth | H-AUTH-01 | cwd `auth/`: `mvn test` | 2 tests aprobados, 0 fallos. |
| 2026-10-26 | auth | H-AUTH-04 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#exposesExceptionDetailsInCurrentLoginErrorResponse test` | 1 test aprobado, caracterizó el 500 actual incluyendo mensaje y clase de excepción. |
| 2026-10-26 | auth | H-AUTH-04 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#hidesExceptionDetailsInLoginErrorResponse test` (antes del cambio productivo) | Falló como se esperaba: el cuerpo devolvía `controlled test failure` en lugar del mensaje genérico. |
| 2026-10-26 | auth | H-AUTH-04 | runner de pruebas VS Code: `hidesExceptionDetailsInLoginErrorResponse` | 1 test aprobado después del cambio; status 500 y mensaje genérico, sin detalles en cuerpo. |
| 2026-10-26 | auth | H-AUTH-01, H-AUTH-04 | cwd `auth/`: `mvn test` | `BUILD SUCCESS`; 3 tests aprobados, 0 fallos. La terminal ya estaba en `auth/`; `Push-Location auth` informó una ruta duplicada antes de Maven. |
| 2026-10-26 | auth | H-AUTH-05 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#currentlyAllowsUnconfiguredCorsOrigin test` (antes del cambio) | 1 test aprobado; el preflight de `https://unapproved.example` recibió 200 y el mismo origen en `Access-Control-Allow-Origin`. |
| 2026-10-26 | auth | H-AUTH-05 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#rejectsUnconfiguredCorsOrigin test` (antes del cambio) | Falló como se esperaba: status 200 en vez de 403 bajo la expresión wildcard. |
| 2026-10-26 | auth | H-AUTH-05 | cwd `auth/`: `mvn -Dtest=AuthResourceTest test` (después del cambio) | `BUILD SUCCESS`; 4 tests aprobados, 0 fallos; incluye origen rechazado y login con secreto test configurado. |
| 2026-10-26 | auth | H-AUTH-02 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#currentlyStoresSeedPasswordInPlaintext test` (antes del cambio) | 1 test aprobado; confirmó almacenamiento literal de la contraseña ficticia seed. |
| 2026-10-26 | auth | H-AUTH-02 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#migratesLegacyPlaintextPasswordIdempotently test` (antes del cambio, tras incorporar la extensión) | Falló como se esperaba: el valor almacenado permaneció plaintext. Un intento anterior no compiló porque faltaba `BcryptUtil`; se añadió la extensión Quarkus justificada por H-AUTH-02. |
| 2026-10-26 | auth | H-AUTH-02 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#migratesLegacyPlaintextPasswordIdempotently test` (después del cambio) | `BUILD SUCCESS`; 1 test aprobado: contraseña legacy migrada a BCrypt, verificada y estable tras segunda inicialización. |
| 2026-10-26 | auth | H-AUTH-02 | cwd `auth/`: `mvn -Dtest=AuthResourceTest test` | `BUILD SUCCESS`; 5 tests aprobados, 0 fallos. |
| 2026-10-26 | auth | H-AUTH-02 | cwd `auth/`: `mvn -Dtest=AuthResourceTest#rejectsInvalidPassword test` | `BUILD SUCCESS`; 1 test aprobado con status 401 y mensaje observado. |
| 2026-10-26 | auth | H-AUTH-06 | Validador CVE Java: coordenadas Quarkus 3.27.4, Commons Text 1.9 y SQLite JDBC 3.50.3.0 | Encontró CVE-2022-42889 en `org.apache.commons:commons-text:1.9`, severidad crítica; advisory recomienda 1.10.0. |
| 2026-10-26 | auth | H-AUTH-06 | Validador CVE Java: seis coordenadas, Commons Text 1.10.0 | No reportó CVE conocido para las coordenadas evaluadas; no representa el grafo transitivo completo. |
| 2026-10-26 | auth | H-AUTH-06, H-AUTH-07 | cwd `auth/`: `mvn -Dtest=AuthResourceTest test` (caracterización antes de refactorizar producción) | `BUILD SUCCESS`; 8 tests aprobados, 0 fallos; status y estructura HTTP preservados. |
| 2026-10-26 | auth | H-AUTH-07 | cwd `auth/`: `mvn -Dtest=AuthResourceTest test` (después de la refactorización) | `BUILD SUCCESS`; 8 tests aprobados, 0 fallos. |
| 2026-10-26 | auth | H-AUTH-06, H-AUTH-07 | cwd `auth/`: `mvn verify` | `BUILD SUCCESS`; 8 tests aprobados, 0 fallos. JaCoCo: clases `AuthResource`, `AuthService`, `LoginResponse`, `LegacyDatabase`, `LegacyTokenUtil`, 84/92 líneas cubiertas (91.3%). |
| 2026-10-27 | auth | H-AUTH-03 | `python tools/lab.py test auth` (baseline, antes del cambio, rama `feature/H-AUTH-03-token-firmado`) | `BUILD SUCCESS`; 15 tests, 0 fallos. |
| 2026-10-27 | auth | H-AUTH-03 | `python tools/lab.py test auth` (primer intento tras el cambio) | `BUILD FAILURE` de compilación: `TOKEN_EXPIRATION_SECONDS` no resuelto en `AuthService`; corregido usando `AuthTokenIssuer.TOKEN_EXPIRATION_SECONDS`. |
| 2026-10-27 | auth | H-AUTH-03 | `python tools/lab.py test auth` (después del cambio) | `BUILD SUCCESS`; 20 tests, 0 fallos (15 `AuthResourceTest` + 5 `AuthTokenIssuerTest`). JaCoCo: `AuthTokenIssuer` 7/7 líneas, `AuthService` 10/10 líneas. |

Añadir cada ejecución posterior con componente `auth`, IDs relacionados, comando, resultado y evidencia real. No atribuir resultados no observados.

## Verificación prevista

- Ejecutar cada prueba de caracterización antes del cambio asociado y repetirla después; ejecutar `mvn verify` con cwd `auth/` para pruebas y reporte JaCoCo.
- Exigir cobertura de al menos 85% del código modificado; documentar numerador, denominador y método. La cobertura global por sí sola no demuestra el porcentaje del diff.
- Revisar Git desde la raíz y confirmar que solo se incluyen cambios aprobados en `auth/` y los artefactos generales permitidos.
- CodeQL y Dependency Review remotos: **no ejecutados** en este recorrido local.
- Marcar un hallazgo `Resuelto` en HALLAZGOS.md solo después de cambio, pruebas y evidencia reales; de lo contrario mantenerlo `Pendiente`.

## Aprobación humana
- Revisor/a: Frank Laura
- Fecha: 2026-10-26
- Decisión: Aprobado para implementar los IDs indicados abajo según el alcance de este plan.
- IDs aprobados: H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07