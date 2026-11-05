# Spec Delta

## Purpose

Define el comportamiento observable del inicio de sesión y protege la comprobación de credenciales frente a entradas que intenten conseguir autenticación no autorizada.

## ADDED Requirements

### Requirement: El login válido conserva su respuesta exitosa

El sistema MUST mantener el endpoint `POST /auth/login` y su respuesta de éxito para las credenciales válidas comprometidas por el Issue #23.

#### Scenario: Inicio de sesión con credenciales válidas

- **WHEN** se envían `student` y `Workshop123!` a `POST /auth/login`
- **THEN** la respuesta es `200` e incluye `user.username` con valor `student` y el campo `token`

### Requirement: Las entradas de inyección no conceden autenticación

El sistema MUST rechazar con `401` los intentos de usar entradas de inyección para autenticarse y MUST NOT devolver un campo `token` en esas respuestas.

#### Scenario: Entrada de inyección en el usuario

- **WHEN** se envía `' OR '1'='1` como usuario y una contraseña no válida a `POST /auth/login`
- **THEN** la respuesta es `401` y no incluye el campo `token`

#### Scenario: Entrada de inyección en la contraseña

- **WHEN** se envía `student` como usuario y `' OR '1'='1` como contraseña a `POST /auth/login`
- **THEN** la respuesta es `401` y no incluye el campo `token`