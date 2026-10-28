"""Runtime configuration for the face service, read from environment variables."""
from __future__ import annotations

import os
from dataclasses import dataclass


def _int(name: str, default: int) -> int:
    return int(os.getenv(name, str(default)))


def _float(name: str, default: float) -> float:
    return float(os.getenv(name, str(default)))


@dataclass(frozen=True)
class Settings:
    api_key: str
    model_pack: str
    model_root: str
    det_size: int
    det_threshold: float
    max_upload_bytes: int
    max_image_side: int
    max_pixels: int
    max_faces: int
    min_face_px: int
    max_concurrency: int
    providers: tuple[str, ...]

    @staticmethod
    def from_env() -> "Settings":
        api_key = os.getenv("FACE_API_KEY", "")
        if len(api_key) < 16:
            raise RuntimeError(
                "FACE_API_KEY must be set (at least 16 characters). "
                "The main backend sends it in the X-API-Key header."
            )
        providers = tuple(
            p.strip() for p in os.getenv("FACE_PROVIDERS", "CPUExecutionProvider").split(",") if p.strip()
        )
        return Settings(
            api_key=api_key,
            model_pack=os.getenv("FACE_MODEL_PACK", "buffalo_l"),
            model_root=os.getenv("FACE_MODEL_ROOT", os.path.expanduser("~/.insightface")),
            det_size=_int("FACE_DET_SIZE", 640),
            det_threshold=_float("FACE_DET_THRESHOLD", 0.5),
            max_upload_bytes=_int("FACE_MAX_UPLOAD_BYTES", 10 * 1024 * 1024),
            max_image_side=_int("FACE_MAX_IMAGE_SIDE", 2560),
            max_pixels=_int("FACE_MAX_PIXELS", 40_000_000),
            max_faces=_int("FACE_MAX_FACES", 120),
            min_face_px=_int("FACE_MIN_FACE_PX", 24),
            max_concurrency=_int("FACE_MAX_CONCURRENCY", 2),
            providers=providers,
        )
