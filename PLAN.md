# Plan de corrección

## api

### H-API-03 — Filtros e inserts concatenados en SQL

- **Estado en HALLAZGOS.md:** `Resuelto`. Este apartado conserva el plan y la
  evidencia histórica; no reabre ni cambia el estado del hallazgo.
- **Hallazgo:** H-API-03, valores de filtros e inserts concatenados en SQL.
- **Problema:** valores de `owner`, título, descripción, estado y prioridad
  incorporados mediante concatenación podían alterar consultas o escrituras;
  comillas en datos válidos también rompían la inserción.
- **Cambio propuesto/aplicado:** usar placeholders SQLite y pasar valores por
  separado en filtros de listado y resumen, inserción y recuperación de la fila
  creada. Mantener rutas, códigos HTTP y forma de respuesta observados porque
  `docs/ENDPOINTS.md` no existe. No añadir una capa ni abstracción adicional
  para resolver únicamente este hallazgo.
- **Archivos:** `api/app/main.py`, `api/tests/test_api.py`.
- **Caracterización previa:** antes del cambio se ejecutó la suite con el caso
  de inserción con apóstrofe caracterizando la respuesta 500 previa y las
  pruebas de listado y resumen existentes. La prueba que ahora se llama
  `test_create_task_with_quote_is_stored_as_data` se ejecutó entonces como
  `test_create_task_with_quote_currently_returns_server_error`.
- **Clean Code:** consultas breves y parametrizadas; sin duplicar la
  serialización ni introducir responsabilidades nuevas en las rutas.

### Registro de ejecución — api / H-API-03

- **Caracterización antes del cambio — 2026-11-02:** desde `api/`,
  `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing`
  terminó con 11 passed y cobertura de `app` de 96%. La inserción con
  apóstrofe devolvía 500 según la expectativa de caracterización de entonces.
- **Prueba de corrección antes del cambio — 2026-11-02:** desde `api/`,
  `.\.venv\Scripts\python.exe -m pytest
  tests/test_api.py::test_create_task_with_quote_is_stored_as_data` terminó
  con 1 failed, como se esperaba: la respuesta fue 500 en vez de 201.
- **Prueba de corrección después del cambio — 2026-11-02:** el mismo comando
  terminó con 1 passed; la tarea con apóstrofe se insertó y apareció en el
  listado.
- **Regresión de filtros después del cambio — 2026-11-02:** desde `api/`,
  `.\.venv\Scripts\python.exe -m pytest
  tests/test_api.py::test_owner_filters_treat_sql_expressions_as_data`
  terminó con 1 passed; listados y resumen trataron la expresión SQL como
  valor literal.
- Este registro reconstruye ejecuciones ya realizadas y reportadas; no se
  ejecutaron pruebas nuevas al crear este apartado.
- **2026-11-02 — verificación del estado actual — api / H-API-03:**
  desde `api/`, `.\.venv\Scripts\python.exe -m pytest --cov=app
  --cov-report=term-missing` terminó con 13 passed y cobertura global de
  `app` de 92%. `api/app/main.py` alcanzó 96%; el módulo preexistente de
  H-API-04, `api/app/services/uploads.py`, alcanzó 83%, por debajo de la meta
  del 85% para el diff conjunto. Ese módulo no se modificó en el alcance
  H-API-03 y queda señalado, no corregido aquí.
