import base64
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


def token(username: str = "student", role: str = "STUDENT") -> str:
    raw = f"{username}|{role}|{int(time.time()) + 3600}|WORKSHOP_SECRET_2026"
    return base64.b64encode(raw.encode()).decode()


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


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
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
        )
    assert response.status_code == 201
    assert (upload_dir / "requirements.txt").exists()


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": "Bearer invalid"})
    assert response.status_code == 401


def test_owner_filter_and_create_currently_allow_another_owner(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        tasks = client.get("/api/tasks", headers=headers(), params={"owner": "trainer"})
        summary = client.get("/api/summary", headers=headers(), params={"owner": "trainer"})
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={
                "title": "Trainer task",
                "description": "Owned by another user",
                "owner": "trainer",
            },
        )

    assert tasks.status_code == 200
    assert [task["owner"] for task in tasks.json()] == ["trainer"]
    assert summary.status_code == 200
    assert summary.json()["owner"] == "trainer"
    assert summary.json()["total"] == 1
    assert created.status_code == 201
    assert created.json()["owner"] == "trainer"


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


def test_upload_requirement_currently_allows_markdown(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.md", b"# Requirements", "text/markdown")},
        )

    assert response.status_code == 201
    assert response.json()["fileName"] == "requirements.md"
    assert (upload_dir / "requirements.md").exists()


def test_upload_requirement_normalizes_filename_inside_upload_dir(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("../escaped.txt", b"outside", "text/plain")},
        )

    assert response.status_code == 201
    assert response.json()["fileName"] == "escaped.txt"
    assert Path(response.json()["storedAt"]).resolve().parent == upload_dir.resolve()
    assert (upload_dir / "escaped.txt").read_bytes() == b"outside"
    assert not (tmp_path / "escaped.txt").exists()


def test_upload_requirement_rejects_file_over_limit(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    oversized_content = b"x" * (1024 * 1024 + 1)
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("large.txt", oversized_content, "text/plain")},
        )

    assert response.status_code == 413
    assert list(upload_dir.iterdir()) == []


def test_upload_requirement_rejects_unapproved_extension(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.exe", b"not allowed", "application/octet-stream")},
        )

    assert response.status_code == 400
    assert not (upload_dir / "requirements.exe").exists()


def test_backend_health_preserves_success_response_shape(tmp_path: Path, monkeypatch) -> None:
    class HealthResponse:
        status = 503
        data = b'{"status":"down"}'

    class HealthManager:
        def request(self, method: str, url: str, timeout: float) -> HealthResponse:
            assert method == "GET"
            assert url == "http://localhost:8080/q/health"
            assert timeout == 3.0
            return HealthResponse()

    monkeypatch.setattr("app.main.urllib3.PoolManager", HealthManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 503,
        "body": '{"status":"down"}',
    }


def test_backend_health_currently_returns_exception_details(tmp_path: Path, monkeypatch) -> None:
    class HealthManager:
        def request(self, method: str, url: str, timeout: float) -> None:
            raise RuntimeError("private health endpoint detail")

    monkeypatch.setattr("app.main.urllib3.PoolManager", HealthManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 0,
        "body": "private health endpoint detail",
    }


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
