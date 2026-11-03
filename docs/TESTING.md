# Evidencia de pruebas

## Alcance: API / H-API-03

Este registro se limita a H-API-03 (parametrización SQL), según el alcance
confirmado para este paso. No es una certificación de cobertura de todos los
comportamientos de los endpoints. Los contratos de referencia están en la
sección `api` de `docs/ENDPOINTS.md`.

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /api/tasks` | Listar tareas semilladas por `student` | Camino feliz | `200`; devolver las tareas del propietario en orden. |
| `GET /api/tasks` | Filtrar con un valor de propietario con sintaxis similar a SQL | Borde H-API-03 | `200`; tratar el valor como dato y devolver lista vacía si no existe ese propietario. |
| `GET /api/tasks` | Base SQLite cerrada antes de la consulta | Error documentado | `500`; cuerpo `Internal Server Error` sin traceback al cliente. |
| `POST /api/tasks` | Crear tarea válida y recuperar resumen | Camino feliz | `201`; devolver la tarea y reflejar la persistencia en el resumen. |
| `POST /api/tasks` | Título/descripción con apóstrofe | Borde H-API-03 | `201`; conservar texto literalmente en respuesta y listado. |
| `POST /api/tasks` | Base SQLite cerrada antes del `INSERT` | Error documentado | `500`; cuerpo `Internal Server Error`. |
| `GET /api/summary` | Resumir tareas de `student` después de crear | Camino feliz | `200`; devolver totales, agrupación por estado y prioridades abiertas. |
| `GET /api/summary` | Filtrar con un valor de propietario con sintaxis similar a SQL | Borde H-API-03 | `200`; devolver total `0` y conservar el valor literal en `owner`. |
| `GET /api/summary` | Base SQLite cerrada antes de la consulta | Error documentado | `500`; cuerpo `Internal Server Error`. |
| `POST /api/requirements` | No ejecutado en este alcance | Fuera de H-API-03 | La carga no ejecuta una consulta SQL parametrizada de tareas. |
| `GET /api/backend-health` | No ejecutado en este alcance | Fuera de H-API-03 | El endpoint no consulta SQLite. |

Las pruebas de respuesta `401`, validación `422`, carga de archivos y
autorización de `owner` quedan fuera de este alcance restringido a H-API-03.
Por ello, esta tabla no afirma que todas las respuestas documentadas en
`docs/ENDPOINTS.md` tengan cobertura de prueba.

## Código de las pruebas H-API-03

Este es el código de las pruebas relacionadas con H-API-03 en
`api/tests/test_api.py`; usa los helpers existentes `create_app`, `headers`,
`TestClient`, `Path` y `pytest` del mismo archivo.

```python
def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={
                "title": "Add tests",
                "description": "Improve coverage",
                "status": "TODO",
                "priority": "HIGH",
            },
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_create_task_with_quote_is_stored_as_data(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={
                "title": "Owner's task",
                "description": "A legitimate value with an apostrophe",
            },
        )
        tasks = client.get("/api/tasks", headers=headers())
    assert response.status_code == 201
    assert response.json()["title"] == "Owner's task"
    assert any(task["title"] == "Owner's task" for task in tasks.json())


def test_owner_filters_treat_sql_expressions_as_data(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    sql_like_owner = "trainer' OR '1'='1"
    with TestClient(app) as client:
        tasks = client.get(
            "/api/tasks",
            headers=headers(),
            params={"owner": sql_like_owner},
        )
        summary = client.get(
            "/api/summary",
            headers=headers(),
            params={"owner": sql_like_owner},
        )
    assert tasks.status_code == 200
    assert tasks.json() == []
    assert summary.status_code == 200
    assert summary.json()["owner"] == sql_like_owner
    assert summary.json()["total"] == 0


@pytest.mark.parametrize(
    ("method", "path", "payload"),
    [
        ("GET", "/api/tasks", None),
        ("POST", "/api/tasks", {"title": "Closed database", "description": "Test"}),
        ("GET", "/api/summary", None),
    ],
)
def test_task_sql_endpoints_return_500_when_database_is_unavailable(
    tmp_path: Path,
    method: str,
    path: str,
    payload: dict[str, str] | None,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    app.state.connection.close()
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.request(method, path, headers=headers(), json=payload)
    assert response.status_code == 500
    assert response.text == "Internal Server Error"
```

## Pruebas ejecutadas

Todas las ejecuciones se realizaron desde `api/` con el entorno virtual local.
No se modificó código de producción en este paso.

| componente e ID | comando | directorio | código de salida | resultado |
|---|---|---|---:|---|
| api / H-API-03 | `.\.venv\Scripts\python.exe -m pytest --cov=app.main --cov-report=term-missing tests/test_api.py::test_lists_seeded_tasks tests/test_api.py::test_creates_task_and_summary tests/test_api.py::test_create_task_with_quote_is_stored_as_data tests/test_api.py::test_owner_filters_treat_sql_expressions_as_data` | `api/` | 0 | 4 passed; cobertura focal de `app.main.py`: 88%. |
| api / H-API-03 | `.\.venv\Scripts\python.exe -m pytest --cov=app.main --cov-report=term-missing tests/test_api.py::test_lists_seeded_tasks tests/test_api.py::test_creates_task_and_summary tests/test_api.py::test_create_task_with_quote_is_stored_as_data tests/test_api.py::test_owner_filters_treat_sql_expressions_as_data tests/test_api.py::test_task_sql_endpoints_return_500_when_database_is_unavailable` | `api/` | 0 | 7 passed; cubre camino feliz, borde SQL y errores SQLite en los tres endpoints con consultas. |
| api / H-API-03 | `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing` | `api/` | 0 | 16 passed; cobertura global `app`: 92%. |

### Cobertura

- **Método:** pytest-cov/Coverage.py con medición de sentencias y ramas,
  usando `--cov=app.main --cov-report=term-missing` para la ejecución focal y
  `--cov=app --cov-report=term-missing` para la ejecución global.
- **Alcance focal H-API-03 (`app/main.py`):** 94 sentencias, 10 no cubiertas;
  16 ramas, 3 parciales; cobertura informada por Coverage.py: **88%**. Supera
  la meta de 85% para el módulo de producción implicado en H-API-03.
- **Cobertura global:** 136 sentencias, 6 no cubiertas; 28 ramas, 5 parciales;
  **92%** global. Desglose: `app/main.py` **96%** y
  `app/services/uploads.py` **83%**.
- **Código de producción modificado en este paso:** ninguno. Por tanto, una
  métrica de cobertura del código de producción modificado no aplica; el 88%
  focal mide el módulo de producción bajo prueba, no el archivo de pruebas.
- Las ejecuciones completas incluyeron warnings deprecación de
  `FastAPI.on_event`; no fallaron pruebas ni se redujeron quality gates.

## Fallos, pendientes y pruebas propuestas

- **Fallos ejecutados:** ninguno en las dos ejecuciones mostradas.
- **Pendiente de cobertura fuera de alcance:** `POST /api/requirements`,
  `GET /api/backend-health` y errores de autenticación/validación no se probaron
  aquí porque el alcance solicitado fue solo H-API-03.
- **Prueba propuesta:** al ampliar el alcance, completar pruebas de cada
  respuesta de error de `docs/ENDPOINTS.md` y probar autorización negativa para
  `owner`; no se afirma aquí el resultado de esos casos.
