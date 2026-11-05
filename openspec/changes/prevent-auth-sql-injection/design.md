# Design

## Context

Ver [proposal.md](proposal.md) para la motivación y [specs/auth-login/spec.md](specs/auth-login/spec.md) para el contrato aprobado.

En `auth/`, `LegacyDatabase.findByCredentials` consulta SQLite con JDBC y concatena `username` y `password` en una sentencia ejecutada mediante `Statement`. Devuelve un `UserRecord` cuando encuentra una fila y `null` cuando no la encuentra o captura un error. `AuthResource.login` convierte el resultado nulo en `401`; para un usuario válido genera el token y devuelve `200`. La prueba existente de `AuthResourceTest` caracteriza el login exitoso, pero no cubre credenciales inválidas ni entradas de inyección.

La restricción de compatibilidad es conservar el endpoint, el éxito de credenciales válidas y el rechazo observable de intentos de inyección definidos en la spec. El cambio no requiere modificar esquema ni datos.

## Goals / Non-Goals

**Goals:**

- Evitar que los valores de usuario y contraseña cambien la interpretación de la consulta de credenciales.
- Mantener la conversión existente de búsqueda sin resultado a `401` y la respuesta exitosa del endpoint.
- Limitar la modificación funcional a `auth/` y a la búsqueda de credenciales.

**Non-Goals:**

- Cambiar endpoints, campos o formato del token, mensajes de error, persistencia o esquema de la base de datos.
- Refactorizar `AuthResource`, reorganizar capas o introducir una dependencia de acceso a datos.
- Ampliar el cambio a `api/`, `web/` o a otros flujos de autenticación.

## Protected Contracts

- `POST /auth/login` mantiene el éxito con `200` para `student` y `Workshop123!`, e incluye `user.username` y `token`, según el Issue #23 y la spec.
- El formato de respuesta exitosa existente no se rediseña; el cambio no debe quitar ni renombrar campos de esa respuesta.
- Los intentos de inyección en usuario o contraseña devuelven `401` y no contienen `token`.
- Las validaciones y respuestas de `AuthResource` que no forman parte del defecto permanecen sin cambios.

## Components Affected

- `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`: único punto funcional previsto para aislar los valores de entrada de la estructura de la consulta.
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: pruebas de caracterización y regresión del contrato HTTP.
- `AuthResource` no requiere cambios: ya traduce la ausencia de usuario a `401` y es responsable de la respuesta de login.

No se modifican componentes ni documentos generales fuera de `auth/` y este cambio OpenSpec.

## Decisions

### Usar parámetros JDBC para los valores de credenciales

La consulta mantendrá sus condiciones de igualdad sobre `username` y `password`, pero los valores se enviarán como parámetros de `PreparedStatement`, en lugar de formar parte del texto SQL. Esto separa los datos de la estructura de la consulta y cumple el criterio técnico explícito del Issue #23 sin cambiar el resultado esperado para credenciales válidas.

Alternativas consideradas:

- Escapar o rechazar caracteres especiales: se descarta porque es frágil frente a las reglas de SQL y puede rechazar valores legítimos; la corrección debe estar en cómo se transmite el dato a la consulta.
- Añadir un ORM o cambiar la capa de persistencia: se descarta porque agrega alcance y dependencias sin ser necesario para corregir esta consulta.
- Refactorizar el endpoint o reorganizar capas: se descarta porque no es necesario para la corrección y aumenta el riesgo de cambiar el contrato protegido.

### Mantener el contrato HTTP en `AuthResource`

Se conserva el flujo existente que mapea una búsqueda sin coincidencia a `401` y una coincidencia a la respuesta exitosa actual. La spec no define el cuerpo del error `401`; por ello, el cambio no establece ni rediseña ese cuerpo.

Alternativa considerada: introducir un nuevo modelo o formato común de errores. Se descarta porque no es necesario para satisfacer la spec y constituiría un cambio de contrato fuera del Issue.

## Incremental Strategy

1. Añadir primero las pruebas HTTP que caracterizan el login válido y las credenciales normales no válidas; conservar la prueba de éxito existente y sus aserciones de `200`, `user.username` y `token`.
2. Añadir casos de inyección por separado para `username` y `password`, verificando `401` y ausencia de `token`. Solo el caso de `password` reproduce el defecto antes de cambiar la consulta; el de `username` pasa con `401` y actúa como regresión.
3. Cambiar únicamente la búsqueda de credenciales en `LegacyDatabase`, manteniendo columnas seleccionadas, correspondencia de parámetros, conversión a `UserRecord` y comportamiento de no coincidencia.
4. Ejecutar `mvn test` desde `auth/` y revisar el contrato de login protegido antes de integrar.

## Characterization Tests

Antes de modificar la consulta, las pruebas deben dejar registrados estos comportamientos:

- Login válido con `student` y `Workshop123!`: `200`, `user.username == "student"` y `token` no nulo.
- Credenciales normales no válidas: `401` y sin campo `token`.
- Payload `' OR '1'='1` como usuario, con contraseña no válida: el resultado objetivo es `401` y sin `token`.
- Payload `' OR '1'='1` como contraseña para `student`: el resultado objetivo es `401` y sin `token`.

Solo el caso de contraseña es una prueba de regresión que se espera que falle contra la consulta vulnerable actual (devuelve `200` con `token`). El caso de usuario con contraseña no válida pasa con `401` antes del cambio, porque `AND` se evalúa antes que `OR` y la condición de contraseña sigue sin cumplirse; se conserva como protección del escenario de la spec, sin demostrar el defecto. No se debe aceptar un `200` ni un token como resultado intermedio válido.

## Risks / Trade-offs

- **Orden o asociación incorrectos de parámetros** → cubrir ambas posiciones de entrada y mantener el caso de login válido.
- **Cambio accidental en el contrato de respuesta** → mantener `AuthResource` sin cambios y verificar las aserciones HTTP existentes.
- **Excepciones JDBC siguen teniendo el comportamiento heredado de no coincidencia** → no ampliar el manejo de errores dentro de este cambio; cualquier modificación de ese contrato requiere otro alcance.
- **Corrección limitada a la consulta de credenciales** → no implica que otras consultas o componentes hayan sido auditados; mantener esta entrega limitada al Issue #23.

## Migration Plan

No hay migración de datos ni cambio de esquema. El cambio se despliega como una actualización de `auth/`, validada con las pruebas del componente.

### Rollback

Si la actualización causa una regresión, se puede revertir el cambio de `auth/` y volver al artefacto anterior sin restaurar datos. El rollback también reintroduce el riesgo de bypass de autenticación, por lo que debe ser temporal y seguido de una corrección antes de volver a exponer el servicio.

## Open Questions

Ninguna que impida esta decisión. El cuerpo exacto de error para `401` no está especificado y permanece fuera de este cambio.