# Revisión de Skills de incidencias

## Pedidos usados

- Registro inicial: `invoca registrar_incidencia con el objeto en incident y approved: true ticket INC-68C439124B25`.
- Ejecución actual: `Ejecuta el mismo Skill sobre un segundo caso, solo hasta preparar el JSON. No necesitas registrarlo. Además, invócalo una vez sin indicar el caso: debe pedirlo, no elegir uno ni inventar evidencia.`
- Prueba sin caso: invocación de `incidencia-cargo-duplicado` sin `case_id` ni escenario que permita identificarlo.

## Resultados observados

- El registro inicial de `caso-01` respondió: `Conflicto: el grupo ya tiene otro contenido y se conservó el ticket original.` No se cambió la correlación ni se repitió el registro con contenido modificado.
- La consulta de `INC-68C439124B25` confirmó que existe, con fecha de creación `2026-11-04T16:07:42.257Z`. El ticket contiene la incidencia original de `caso-01` para `TRX-DEMO-101`, clasificada `P2`, con resultado pendiente de confirmación final.
- En la invocación sin caso, el MCP listó `caso-01` (transferencia), `caso-02` (acceso) y `caso-03` (posible cargo duplicado); se preguntó: `¿Qué caso deseas analizar: caso-01, caso-02 o caso-03?` Tras la selección del usuario (`caso-01`), se obtuvo la evidencia y se preparó la incidencia sin registrarla.
- El análisis de `caso-01` cuenta `evt-102` y `evt-103` como dos errores distintos; la segunda línea de `evt-102` es una copia y `evt-104` queda fuera por otra correlación. El JSON existente es [caso-01/incidencia.json](caso-01/incidencia.json); no incluye `approved`.
- Para `caso-02`, los eventos `evt-202` y `evt-203` forman el grupo de errores `AUTH_DEPENDENCY_UNAVAILABLE` de `LOGIN-DEMO-201`. La segunda línea de `evt-202` es una copia y no aumenta `occurrences`; `evt-204` pertenece a otra correlación y queda fuera del grupo. Las fechas del JSON están convertidas a UTC y los campos sensibles están redactados.
- JSON preparado: [caso-02/incidencia.json](caso-02/incidencia.json). No se invocó `registrar_incidencia` para este caso.

## Correcciones al Skill

- Versión `0.3.0`: alcance y descripción ahora cubren los tres casos del reto conforme a las reglas MCP; el caso-02 no debe descartarse por no tratarse de una duplicidad de cargos.
- Sin `case_id` y sin contexto inequívoco, el Skill debe listar las opciones, pedir selección y detenerse antes de obtener evidencia o preparar el JSON.
- Se consolidaron instrucciones duplicadas sobre movimientos y clasificación por `event_id`, y se ordenó la numeración del procedimiento.

## Ticket y reintento

- Ticket consultado: `INC-68C439124B25`.
- Respuesta de consulta: ticket encontrado; conserva el contenido original de `caso-01` y la fecha de creación indicada arriba.
- Respuesta del reintento de registro con contenido distinto: conflicto; se conservó el ticket original. El ID no fue sustituido ni se generó otro.

## Pendientes

- `caso-02` queda solo preparado; no está registrado ni aprobado para registro.
- Sigue pendiente resolver la diferencia entre el objeto local inicial de `caso-01` y el contenido ya conservado en el ticket. No se modificó el registro existente.