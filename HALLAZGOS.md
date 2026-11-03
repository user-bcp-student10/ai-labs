# Hallazgos

## Contrato observado — api

No existe `docs/ENDPOINTS.md`; lo siguiente se deriva del código y no representa
un contrato acordado:

- `GET /api/tasks`: requiere el token heredado; acepta `owner` opcional (por
  defecto usa el usuario del token) y devuelve tareas con `id`, `title`,
  `description`, `status`, `owner`, `priority` y `createdAt`
  (`api/app/main.py:67`, `api/app/main.py:68`, `api/app/main.py:75`).
- `POST /api/tasks`: requiere el token heredado, acepta un objeto JSON y
  responde `201` con la tarea creada (`api/app/main.py:77`,
  `api/app/main.py:78`, `api/app/main.py:88`).
- `GET /api/summary`: requiere el token heredado, acepta `owner` opcional y
  devuelve `owner`, `total`, `byStatus` y `highPriorityOpen`
  (`api/app/main.py:90`, `api/app/main.py:91`, `api/app/main.py:94`).
- `POST /api/requirements`: requiere el token heredado, recibe `file` como
  multipart y responde `201` con propietario, nombre, ruta guardada y tamaño
  (`api/app/main.py:107`, `api/app/main.py:108`, `api/app/main.py:112`).
- `GET /api/backend-health`: requiere el token heredado y devuelve
  `requestedBy`, `status` y `body` del servicio de salud configurado
  (`api/app/main.py:114`, `api/app/main.py:115`, `api/app/main.py:119`).
- El token observado es Base64 de campos separados por `|`; contiene usuario,
  rol, expiración y un secreto fijo (`api/app/main.py:14`,
  `api/app/main.py:19`, `api/app/main.py:22`, `api/app/main.py:24`).

## Seguimiento — api

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | Token Base64 con secreto embebido | Vulnerabilidad | Base64 no firma ni protege el contenido; el secreto está fijado en el código y la validación no comprueba firma, emisor ni audiencia. Un token manipulable puede permitir suplantar identidades; la expiración por sí sola no acredita autenticidad. | api/app/main.py:14, api/app/main.py:20, api/app/main.py:22 | alta | Aprobado |
| H-API-02 | api | El cliente puede consultar o asignar otro `owner` | Vulnerabilidad | `owner` de la consulta reemplazaba la identidad autenticada y el cuerpo de creación permitía elegir propietario. **Resolución:** listado, resumen y creación rechazan con `403` los propietarios distintos; se agregaron pruebas de autorización y no persistencia. | api/app/main.py:69-104, api/tests/test_api.py:62-107 | alta | Resuelto |
| H-API-03 | api | Filtros e inserts concatenados en SQL | Vulnerabilidad | Los valores procedentes de query y JSON se concatenaban en sentencias SQL sin parámetros. **Resolución:** filtros, inserción y recuperación por ID usan consultas parametrizadas. | api/app/main.py:70, api/app/main.py:84, api/app/main.py:88, api/app/main.py:97 | alta | Resuelto |
| H-API-04 | api | Se conserva directamente el nombre del archivo subido | Vulnerabilidad | El nombre proporcionado por el cliente se unía al directorio y se escribía sin validaciones. **Resolución:** normalización de nombre, confinamiento, allowlist `.txt`/`.md` y límite de 1 MiB con lectura por bloques. | api/app/services/uploads.py:21, api/app/services/uploads.py:30, api/app/services/uploads.py:43, api/app/services/uploads.py:45 | alta | Resuelto |
| H-API-05 | api | Dependencias `urllib3 2.5.0` y `python-multipart 0.0.21` fijadas | Vulnerabilidad | El manifiesto fija las versiones señaladas por el reto. Su exposición a avisos aplicables y la versión corregida requieren una evaluación SCA; no se confirma aquí un CVE concreto. | api/requirements.txt:4, api/requirements.txt:5 | media | Aprobado |
| H-API-06 | api | Rutas, persistencia, reglas y serialización en un solo módulo | Refactor | `create_app` inicializa SQLite y contiene las rutas, consultas, lógica de negocio y mapeo de respuestas. Esto acopla responsabilidades y hace más difícil probarlas y modificar cada capa sin regresiones. | api/app/main.py:29, api/app/main.py:56, api/app/main.py:67, api/app/main.py:107 | baja | Aprobado |
| H-API-07 | api | Tipado crudo, excepciones amplias y condiciones anidadas | Refactor | Los contratos usan `dict[str, Any]`, se capturan excepciones genéricas y `summary` anida condiciones. Esto debilita la validación y el manejo predecible de errores; además, las respuestas incluyen texto de excepciones. | api/app/main.py:17, api/app/main.py:25, api/app/main.py:78, api/app/main.py:101, api/app/main.py:120 | baja | Aprobado |

## Candidatos para revisión humana — api

### H-API-08 — La carga de un nombre repetido reemplaza el archivo anterior
- Estado: CANDIDATO
- Evidencia: `api/app/services/uploads.py:26-30` resuelve siempre el destino a partir del nombre normalizado y `:48` lo reemplaza con `os.replace`. `docs/ENDPOINTS.md:349-351` registra que la política para nombres repetidos sigue abierta. Una prueba puntual con `TestClient` envió dos cargas `same.txt` y confirmó que el contenido final fue el segundo.
- Impacto: una carga puede eliminar silenciosamente el contenido previamente almacenado con ese nombre, incluso si la realiza otro usuario autenticado; el directorio de carga es compartido y no está segmentado por propietario.
- Prioridad propuesta: Media
- Contrato potencialmente afectado: `POST /api/requirements`, en particular la persistencia asociada a `fileName`/`storedAt`; falta acordar el comportamiento ante colisiones.
- Cómo validarlo: probar dos solicitudes con el mismo nombre y contenidos distintos, también con identidades distintas, y confirmar el comportamiento acordado (rechazar colisión, versionar o aislar por propietario) sin perder contenido anterior.

### H-API-09 — `POST /api/tasks` convierte valores no textuales en cadenas y los persiste
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:80-87` recibe `dict[str, Any]`, aplica `str()` a los campos y ejecuta el `INSERT` sin validar tipos ni dominios. `docs/ENDPOINTS.md:137-141` describe los campos de tarea como cadenas. Una prueba puntual envió objeto, `null` y lista en campos textuales y valores de estado/prioridad no reconocidos; la API respondió `201` y persistió sus representaciones como texto.
- Impacto: solicitudes con tipos o valores inesperados producen registros persistentes difíciles de interpretar; estados no reconocidos también alteran la clasificación que realiza `/api/summary`.
- Prioridad propuesta: Media
- Contrato potencialmente afectado: cuerpo y respuesta de `POST /api/tasks` y el resumen `GET /api/summary`; los tipos son observables, pero los valores permitidos y la obligatoriedad requieren decisión de producto.
- Cómo validarlo: acordar primero campos obligatorios y dominios de `status`/`priority`; después probar campos con tipos incorrectos, campos ausentes y valores fuera de dominio, verificando rechazo explícito y ausencia de inserción.

### H-API-10 — La limpieza de SQLite usa el hook de apagado deprecado
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:128-130` registra `connection.close()` con `@app.on_event("shutdown")`. `python tools/lab.py test api` pasó (16 pruebas; cobertura total 92.07%) y emitió advertencias de deprecación para `on_event`.
- Impacto: el cierre depende de una API de ciclo de vida deprecada; al migrar FastAPI, el registro podría dejar de ser compatible y la conexión podría no cerrarse en el apagado.
- Prioridad propuesta: Baja
- Contrato potencialmente afectado: ciclo de vida de la aplicación y liberación de la conexión SQLite; no cambia el formato HTTP mientras el hook siga operativo.
- Cómo validarlo: probar creación y apagado de la aplicación mediante el ciclo de vida vigente, verificar que la conexión se cierre y ejecutar `python tools/lab.py test api` sin advertencias de deprecación relacionadas.
