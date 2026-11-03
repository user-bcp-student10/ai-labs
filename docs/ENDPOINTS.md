# Contrato HTTP

## API (`api/`)

### Estado del contrato

- **Contrato observado:** se documenta el comportamiento del código actual de
  `api/app/main.py` y `api/app/services/uploads.py`. No existe una aprobación
  de contrato independiente en este documento.
- **Contrato acordado:** no consta un contrato acordado distinto del observado.
- **Riesgos y preguntas abiertas:** la autenticación usa un token Base64
  heredado, no JWT firmado; `owner` enviado por el cliente puede seleccionar
  otro propietario en listado, resumen y creación; `/api/backend-health`
  devuelve texto crudo de errores de conexión. El emisor no tiene contrato JWT
  documentado. La política de sobrescritura de una carga con nombre repetido
  tampoco está acordada.

Todos los endpoints requieren el header `Authorization`. El formato observado
es `Bearer <token-base64>`, donde el texto decodificado tiene
`username|role|expiry|WORKSHOP_SECRET_2026`; `expiry` es epoch en segundos.
Esta credencial es solo para el laboratorio local. El código reemplaza el texto
`Bearer ` antes de decodificar y no verifica una firma criptográfica.

Para los ejemplos se usa este token de laboratorio, generado con vencimiento
epoch `4102444800` (2100-01-01); no usarlo fuera de la instancia local:

```text
Authorization: Bearer c3R1ZGVudHxTVFVERU5UfDQxMDI0NDQ4MDB8V09SS1NIT1BfU0VDUkVUXzIwMjY=
```

Los ejemplos de respuesta reflejan ejecuciones locales de la API con una base
temporal. Los IDs y marcas de tiempo dependen del estado de esa base. Los campos
se infieren del serializador y de los valores SQLite observados en
`api/app/main.py:56-65`, `api/app/main.py:37-43`.

---

## `GET /api/tasks`

### 1. Qué hace

Lista las tareas del propietario indicado; si no se indica `owner` o está
vacío, lista las tareas del usuario extraído del token.

### 2. Autenticación

Obligatoria: header `Authorization: Bearer <token-base64>` con el formato
heredado descrito arriba (`api/app/main.py:19-28`).

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---:|---|
| `owner` (query) | string | No | Propietario por el que filtrar. Si falta o es cadena vacía, se usa `username` del token. El valor se pasa parametrizado a SQLite. |
| `Authorization` (header) | string | Sí | Token heredado en Base64, precedido por `Bearer `. |

Ejemplo completo:

```http
GET /api/tasks?owner=student HTTP/1.1
Host: localhost:8000
Authorization: Bearer c3R1ZGVudHxTVFVERU5UfDQxMDI0NDQ4MDB8V09SS1NIT1BfU0VDUkVUXzIwMjY=
```

### 4. Response exitosa

**Estado:** `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| respuesta | array de objetos | Lista ordenada por `id` ascendente; puede estar vacía. |
| `id` | integer | Identificador de la tarea. |
| `title` | string | Título. |
| `description` | string | Descripción. |
| `status` | string | Estado almacenado. |
| `owner` | string | Propietario almacenado. |
| `priority` | string | Prioridad almacenada. |
| `createdAt` | string | Fecha de creación serializada por SQLite como `YYYY-MM-DD HH:MM:SS`. |

Ejemplo completo:

```json
[
  {
    "id": 1,
    "title": "Refactor login",
    "description": "Separar responsabilidades en frontend",
    "status": "TODO",
    "owner": "student",
    "priority": "HIGH",
    "createdAt": "2026-11-02 22:27:32"
  },
  {
    "id": 2,
    "title": "Review API",
    "description": "Revisar seguridad de endpoints",
    "status": "IN_PROGRESS",
    "owner": "student",
    "priority": "MEDIUM",
    "createdAt": "2026-11-02 22:27:32"
  }
]
```

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token que no se puede decodificar/validar. El detalle depende de la excepción interna. | `{"detail":"Invalid token: Incorrect padding"}` |
| `500 Internal Server Error` | Error no manejado, por ejemplo fallo de SQLite. La respuesta observada de FastAPI sin debug es texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- El `owner` de query se acepta como valor de filtro sin comprobar que
  corresponda a la identidad del token. Los valores SQL se parametrizan
  (`api/app/main.py:70-73`).
- El token heredado no es JWT firmado y su secreto estático no ofrece prueba
  criptográfica de autenticidad.

---

## `POST /api/tasks`

### 1. Qué hace

Crea una tarea y devuelve la fila persistida.

### 2. Autenticación

Obligatoria: header `Authorization: Bearer <token-base64>` con el formato
heredado descrito arriba (`api/app/main.py:19-28`).

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---:|---|
| cuerpo | objeto JSON | Sí | El código espera un objeto. Si falta o no es un objeto válido, FastAPI puede responder `422`. |
| `title` | string | No | Título; por defecto `""`. El valor se convierte a string. |
| `description` | string | No | Descripción; por defecto `""`. El valor se convierte a string. |
| `status` | string | No | Estado; por defecto `"TODO"`. El valor se convierte a string. |
| `priority` | string | No | Prioridad; por defecto `"MEDIUM"`. El valor se convierte a string. |
| `owner` | string | No | Propietario; por defecto usa `username` del token. Si se envía, el código actual permite asignar otro propietario. |
| `Authorization` (header) | string | Sí | Token heredado en Base64, precedido por `Bearer `. |

Ejemplo completo:

```http
POST /api/tasks HTTP/1.1
Host: localhost:8000
Authorization: Bearer c3R1ZGVudHxTVFVERU5UfDQxMDI0NDQ4MDB8V09SS1NIT1BfU0VDUkVUXzIwMjY=
Content-Type: application/json

{
  "title": "Prepare release",
  "description": "Verify requirements",
  "status": "TODO",
  "priority": "HIGH"
}
```

### 4. Response exitosa

**Estado:** `201 Created`.

| campo | tipo | descripción |
|---|---|---|
| `id` | integer | Identificador generado por SQLite. |
| `title` | string | Título persistido. |
| `description` | string | Descripción persistida. |
| `status` | string | Estado persistido. |
| `owner` | string | Propietario persistido. |
| `priority` | string | Prioridad persistida. |
| `createdAt` | string | Fecha de creación con formato `YYYY-MM-DD HH:MM:SS`. |

Ejemplo completo:

```json
{
  "id": 4,
  "title": "Prepare release",
  "description": "Verify requirements",
  "status": "TODO",
  "owner": "student",
  "priority": "HIGH",
  "createdAt": "2026-11-02 22:27:32"
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido. | `{"detail":"Invalid token: Incorrect padding"}` |
| `422 Unprocessable Entity` | Cuerpo ausente, JSON mal formado o raíz que no cumple el tipo objeto esperado por FastAPI. | `{"detail":[{"type":"missing","loc":["body"],"msg":"Field required","input":null}]}` |
| `500 Internal Server Error` | Error no manejado durante inserción, commit o consulta SQLite. Respuesta observada sin debug: texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- Los valores del `INSERT` y la consulta de recuperación usan parámetros
  SQLite (`api/app/main.py:84-93`), por lo que texto con comillas se almacena
  como dato.
- El campo `owner` del cliente puede asignar tareas a un usuario diferente al
  del token; el comportamiento está observado, no implica autorización.
- El token heredado no tiene firma criptográfica.

---

## `GET /api/summary`

### 1. Qué hace

Resume el total de tareas, su distribución por estado y las tareas abiertas de
prioridad alta para un propietario.

### 2. Autenticación

Obligatoria: header `Authorization: Bearer <token-base64>` con el formato
heredado descrito arriba (`api/app/main.py:19-28`).

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---:|---|
| `owner` (query) | string | No | Propietario a resumir. Si falta o está vacío, se usa `username` del token. |
| `Authorization` (header) | string | Sí | Token heredado en Base64, precedido por `Bearer `. |

Ejemplo completo:

```http
GET /api/summary?owner=student HTTP/1.1
Host: localhost:8000
Authorization: Bearer c3R1ZGVudHxTVFVERU5UfDQxMDI0NDQ4MDB8V09SS1NIT1BfU0VDUkVUXzIwMjY=
```

### 4. Response exitosa

**Estado:** `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| `owner` | string | Propietario resumido. |
| `total` | integer | Número total de tareas. |
| `byStatus` | object de string a integer | Conteo de tareas agrupado por valor de `status`; puede ser `{}`. |
| `highPriorityOpen` | integer | Tareas con `priority == "HIGH"` cuyo estado no sea `DONE` ni `ARCHIVED`. |

Ejemplo completo:

```json
{
  "owner": "student",
  "total": 3,
  "byStatus": {
    "TODO": 2,
    "IN_PROGRESS": 1
  },
  "highPriorityOpen": 2
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido. | `{"detail":"Invalid token: Incorrect padding"}` |
| `500 Internal Server Error` | Error no manejado al consultar SQLite. Respuesta observada sin debug: texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- El valor `owner` de query se usa como parámetro SQL, pero no se verifica
  contra la identidad del token (`api/app/main.py:96-102`).
- La identidad no puede considerarse fiable mientras se acepte el token Base64
  heredado sin firma.

---

## `POST /api/requirements`

### 1. Qué hace

Guarda localmente un documento de requerimientos y devuelve metadatos de la
carga.

### 2. Autenticación

Obligatoria: header `Authorization: Bearer <token-base64>` con el formato
heredado descrito arriba (`api/app/main.py:19-28`).

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---:|---|
| `file` (multipart) | archivo binario | Sí | Se acepta extensión `.txt` o `.md` (sin distinguir mayúsculas/minúsculas), máximo 1 MiB. El nombre se normaliza al nombre base. |
| `Authorization` (header) | string | Sí | Token heredado en Base64, precedido por `Bearer `. |

Ejemplo completo:

```http
POST /api/requirements HTTP/1.1
Host: localhost:8000
Authorization: Bearer c3R1ZGVudHxTVFVERU5UfDQxMDI0NDQ4MDB8V09SS1NIT1BfU0VDUkVUXzIwMjY=
Content-Type: multipart/form-data; boundary=ExampleBoundary

--ExampleBoundary
Content-Disposition: form-data; name="file"; filename="requirements.txt"
Content-Type: text/plain

Login must be accessible
--ExampleBoundary--
```

### 4. Response exitosa

**Estado:** `201 Created`.

| campo | tipo | descripción |
|---|---|---|
| `owner` | string | `username` extraído del token. |
| `fileName` | string | Nombre base normalizado guardado. |
| `storedAt` | string | Ruta del archivo guardado bajo el directorio configurado. |
| `size` | integer | Tamaño del contenido en bytes. |

Ejemplo completo (la ruta absoluta depende de la configuración del host):

```json
{
  "owner": "student",
  "fileName": "requirements.txt",
  "storedAt": "C:\\Users\\Usuario\\AppData\\Local\\Temp\\tmpptf8ibqv\\uploads\\requirements.txt",
  "size": 24
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido. | `{"detail":"Invalid token: Incorrect padding"}` |
| `400 Bad Request` | Nombre vacío o extensión no permitida. | `{"detail":"Invalid file"}` |
| `413 Request Entity Too Large` | Contenido leído supera 1 MiB. | `{"detail":"File too large"}` |
| `422 Unprocessable Entity` | El campo multipart `file` falta o la petición no se puede analizar como multipart válido. | `{"detail":[{"type":"missing","loc":["body","file"],"msg":"Field required"}]}` |
| `500 Internal Server Error` | Error de escritura capturado por el servicio. | `{"detail":"Could not store file"}` |
| `500 Internal Server Error` | Otro error no manejado al preparar/rresolver la ruta. Respuesta observada de FastAPI sin debug: texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- El servicio permite solo `.txt`/`.md`, normaliza separadores de ruta, verifica
  el directorio destino y lee en bloques de 64 KiB hasta el límite de 1 MiB
  (`api/app/services/uploads.py:10-11`, `api/app/services/uploads.py:21-45`).
- Una carga válida con el mismo nombre reemplaza el archivo de destino mediante
  `os.replace` (`api/app/services/uploads.py:48`). La política de sobrescritura
  es una pregunta abierta.
- El token heredado no acredita criptográficamente la identidad indicada como
  `owner`.

---

## `GET /api/backend-health`

### 1. Qué hace

Consulta el endpoint de salud de autenticación configurado y devuelve el
resultado asociado al usuario solicitante.

### 2. Autenticación

Obligatoria: header `Authorization: Bearer <token-base64>` con el formato
heredado descrito arriba (`api/app/main.py:19-28`).

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---:|---|
| `Authorization` (header) | string | Sí | Token heredado en Base64, precedido por `Bearer `. |
| `AUTH_HEALTH_URL` (configuración de servidor) | string URL | No | URL consultada. Por defecto `http://localhost:8080/q/health`; no es un parámetro enviado por el cliente. |

Ejemplo completo:

```http
GET /api/backend-health HTTP/1.1
Host: localhost:8000
Authorization: Bearer c3R1ZGVudHxTVFVERU5UfDQxMDI0NDQ4MDB8V09SS1NIT1BfU0VDUkVUXzIwMjY=
```

### 4. Response exitosa

**Estado HTTP de la API:** `200 OK`, incluso si el servicio consultado responde
con otro estado HTTP.

| campo | tipo | descripción |
|---|---|---|
| `requestedBy` | string | `username` del token. |
| `status` | integer | Código HTTP del servicio de salud; `0` si la solicitud o decodificación falla. |
| `body` | string | Cuerpo remoto decodificado como UTF-8; en error contiene el texto de la excepción. |

Ejemplo completo con una respuesta de salud `200` cuyo cuerpo es
`{"status":"UP"}`:

```json
{
  "requestedBy": "student",
  "status": 200,
  "body": "{\"status\":\"UP\"}"
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido. | `{"detail":"Invalid token: Incorrect padding"}` |

Los errores de conexión al servicio remoto **no** producen un estado HTTP de
error en este endpoint: el código los convierte en respuesta `200 OK` con
`status: 0` y el texto de excepción en `body`. Ejemplo observado con una
excepción controlada:

```json
{
  "requestedBy": "student",
  "status": 0,
  "body": "private health endpoint detail"
}
```

### 6. Notas de seguridad

- La URL de destino procede de configuración de servidor, no de un parámetro
  de esta ruta (`api/app/main.py:119-121`).
- El texto de excepción del cliente HTTP se devuelve sin sanitizar en `body`;
  puede incluir detalles internos.
- La identidad se deriva del token Base64 heredado y no de un JWT firmado.
