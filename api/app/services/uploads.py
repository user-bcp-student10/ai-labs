from __future__ import annotations

import os
import tempfile
from pathlib import Path
from typing import Any

from fastapi import HTTPException, UploadFile

MAX_UPLOAD_SIZE = 1024 * 1024
UPLOAD_CHUNK_SIZE = 64 * 1024
ALLOWED_EXTENSIONS = {".txt", ".md"}


async def store_requirement(
    upload: UploadFile,
    upload_directory: str,
    owner: str,
) -> dict[str, Any]:
    original_name = upload.filename or ""
    normalized_name = Path(original_name.replace("\\", "/")).name
    extension = Path(normalized_name).suffix.lower()
    if not normalized_name or extension not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="Invalid file")

    directory = Path(upload_directory)
    directory.mkdir(parents=True, exist_ok=True)
    destination = directory / normalized_name
    resolved_directory = directory.resolve()
    if destination.resolve().parent != resolved_directory:
        raise HTTPException(status_code=400, detail="Invalid file")

    staged_path: Path | None = None
    size = 0
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=resolved_directory,
            prefix=".upload-",
            delete=False,
        ) as staged_file:
            staged_path = Path(staged_file.name)
            while content := await upload.read(UPLOAD_CHUNK_SIZE):
                size += len(content)
                if size > MAX_UPLOAD_SIZE:
                    raise HTTPException(status_code=413, detail="File too large")
                staged_file.write(content)
        os.replace(staged_path, destination)
    except HTTPException:
        if staged_path is not None:
            staged_path.unlink(missing_ok=True)
        raise
    except OSError as error:
        if staged_path is not None:
            staged_path.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail="Could not store file") from error
    finally:
        await upload.close()

    return {
        "owner": owner,
        "fileName": normalized_name,
        "storedAt": str(destination),
        "size": size,
    }
