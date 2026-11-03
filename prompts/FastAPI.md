### API de requerimientos · Python y FastAPI

AMBITO
Trabaja desde la raíz de ai-labs. Analiza únicamente el componente api/;
no leas ni modifiques auth/ ni web/. Lee README.md, AGENTS.md, RETO.md
y docs/GOOD_PRACTICES.md. Consulta docs/ENDPOINTS.md si existe. Este pedido
es de solo lectura: no escribas archivos ni cambies código.

CONTEXTO
api/ es una API en Python y FastAPI que expone datos y operaciones.
Analiza sus rutas, validaciones, autenticación, autorización, acceso a SQLite,
carga de archivos y respuestas. Usa api/app/main.py, api/tests/ y los
manifiestos de dependencias como evidencia. No des por confirmados los
candidatos de RETO.md.

OBJETIVO
Determinar qué operaciones expone la API, qué datos afectan y si los
controles observados protegen correctamente cada operación. Comprueba los
candidatos del reto: verificación de tokens, autorización por identidad,
consultas SQL, carga de archivos, dependencias y separación de
responsabilidades.

RESTRICCIONES
Usa solo evidencia local y datos ficticios. No envíes solicitudes a servicios,
no ejecutes pruebas de explotación y no incluyas payloads. Conserva los
contratos observados; no inventes endpoints ni formatos. Si algo no puede
comprobarse con la evidencia disponible, indícalo.

CRITERIOS DE ACEPTACION
Para cada hallazgo confirmado, asigna un ID H-API-NN y cita archivo y línea.
Describe la operación y el flujo de datos implicados, y separa hechos de
hipótesis. Incluye también los candidatos no encontrados o no comprobados.
Propón pruebas locales válidas, de borde y de error, con el resultado esperado
según el contrato observado; no afirmes que fueron ejecutadas.

FORMATO DE RESPUESTA
Tabla: ID | operación/área | afirmación | evidencia archivo:línea |
estado (Confirmado/No encontrado/No comprobado) | prueba propuesta.

Termina con los contratos observados, las preguntas abiertas y los comandos
de prueba o escaneo que no se ejecutaron.