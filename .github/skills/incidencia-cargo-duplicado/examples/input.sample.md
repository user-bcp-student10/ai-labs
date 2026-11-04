# Entrada de ejemplo: cargo posiblemente duplicado

- **Caso:** `caso-03`.
- **Fuente:** Obtener mediante `obtener_caso` con `case_id: "caso-03"` (`caso-03-cargos.log`).
- **Contexto funcional:** El sistema de tarjetas detectó dos movimientos asociados a un mismo pago. Operaciones necesita evidencia para conciliarlos antes de decidir si corresponde una devolución.
- **Resultado esperado:** Preparar la incidencia normalizada, relacionando la alerta con los movimientos de la misma operación, deduplicando eventos repetidos, preservando identificadores técnicos, redactando el dato de tarjeta e indicando el resultado de conciliación faltante.
- **Autorización:** No registrar hasta presentar el JSON y recibir aprobación explícita. Una alerta repetida con el mismo `event_id` no cuenta como otro incidente.
