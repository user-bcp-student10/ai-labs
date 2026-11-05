# Proposal

## Why

El Issue [#23](https://github.com/user-bcp-student10/ai-labs/issues/23) confirma que la búsqueda de credenciales del login permite alterar su significado con entradas controladas por el usuario, lo que puede permitir autenticarse sin conocer la contraseña. Es un bypass de autenticación de severidad alta y debe corregirse sin cambiar el contrato de inicio de sesión comprometido por el issue.

## What Changes

- El login rechazará entradas que intenten alterar la evaluación de las credenciales para obtener autenticación no autorizada.
- Las credenciales válidas conservarán la respuesta de éxito establecida por el Issue #23.

## Alcance

- Solo el componente `auth/` y el comportamiento de `POST /auth/login` relacionado con la comprobación de credenciales.
- Verificación de que los intentos de inyección no autentiquen al solicitante ni expongan un token.

## Fuera de alcance

- Cambios en `api/`, `web/` u otros consumidores.
- Cambios de endpoint, formato de respuesta exitosa, formato de token, almacenamiento de credenciales o funcionalidades ajenas al login.

## Comportamiento a preservar

- `POST /auth/login` con `student` y `Workshop123!` responde `200` e incluye `user.username` y `token`, conforme al Issue #23.
- Los demás contratos documentados para API en `docs/ENDPOINTS.md` no se modifican. Ese documento no especifica `POST /auth/login`; por ello, el Issue #23 es la fuente disponible para el contrato de auth en este cambio.

## Capabilities

### New Capabilities

- `auth-login`: autenticación de usuarios mediante el endpoint de login, incluyendo el rechazo de entradas que intenten evadir la comprobación de credenciales.

### Modified Capabilities

Ninguna. El repositorio no tiene specs existentes para modificar.

## Impact

- Código y pruebas del componente `auth/`.
- El endpoint existente `POST /auth/login`; no se agregan ni eliminan endpoints ni dependencias.

## Riesgos, supuestos y preguntas abiertas

- `docs/ENDPOINTS.md` no incluye el contrato de auth. Se preserva aquí el comportamiento explícito del Issue #23 sin inferir otros detalles del formato de respuesta.
- El Issue fija `401` y la ausencia de `token` para los intentos de inyección, pero no define el cuerpo de error; este cambio no introduce un nuevo requisito sobre ese cuerpo.