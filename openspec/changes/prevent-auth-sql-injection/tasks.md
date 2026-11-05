# Tasks

## 1. Pruebas de caracterización HTTP

- [ ] 1.1 Conservar la caracterización del login válido (`200`, `user.username` y `token`) y añadir credenciales normales no válidas (`401`, sin `token`) en `AuthResourceTest`; verificar con `mvn -Dtest=AuthResourceTest test` desde `auth/`.
- [ ] 1.2 Añadir un caso de inyección en `username` con `' OR '1'='1`; ejecutar ese caso antes del cambio funcional y confirmar que reproduce el bypass actual, no un fallo de configuración.
- [ ] 1.3 Añadir un caso de inyección en `password` con `' OR '1'='1`; ejecutarlo antes del cambio funcional y confirmar que reproduce el bypass actual, no un fallo de configuración.

## 2. Consulta de credenciales y regresión

- [ ] 2.1 Cambiar únicamente la consulta de credenciales en `LegacyDatabase` para usar `PreparedStatement` con parámetros para `username` y `password`, preservando el mapeo de resultados; verificar que `mvn -Dtest=AuthResourceTest test` pasa desde `auth/`.
- [ ] 2.2 Ejecutar `mvn test` desde `auth/` y confirmar que toda la suite pasa, incluidos login válido y los dos rechazos de inyección con `401` sin `token`.