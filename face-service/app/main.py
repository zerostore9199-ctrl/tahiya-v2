"""Stateless face service.

POST /embed  — detects every face in an image and returns its bounding box,
               detection score and a 512-d ArcFace embedding.
GET  /health — liveness/readiness probe.

The service never stores images or embeddings and has no database access;
matching against enrolled soldiers happens in the main backend.
"""
from __future__ import annotations

import hmac
import logging
import time
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, File, Header, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse

from .config import Settings
from .imaging import ImageError, decode_image

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("face-service")

_state: dict = {}


def build_engine(settings: Settings):
    from .engine import FaceEngine
    return FaceEngine(settings)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = Settings.from_env()
    _state["settings"] = settings
    if "engine" not in _state:  # tests may inject a fake engine
        _state["engine"] = build_engine(settings)
    yield
    _state.clear()


app = FastAPI(
    title="Military Attendance — Face Service",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url=None,
)


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    expected = _state["settings"].api_key
    if not x_api_key or not hmac.compare_digest(x_api_key.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail={"code": "UNAUTHORIZED", "message": "Invalid API key"})


@app.exception_handler(ImageError)
async def image_error_handler(_: Request, exc: ImageError):
    return JSONResponse(status_code=422, content={"detail": {"code": exc.code, "message": str(exc)}})


@app.get("/health")
def health():
    engine = _state.get("engine")
    return {"status": "UP" if engine else "STARTING", "model": getattr(engine, "model_name", None)}


@app.post("/embed", dependencies=[Depends(require_api_key)])
async def embed(image: UploadFile = File(...)):
    settings: Settings = _state["settings"]
    # Read at most max+1 bytes so an oversized upload is rejected without buffering it all.
    data = await image.read(settings.max_upload_bytes + 1)
    img, scale = decode_image(data, settings.max_upload_bytes, settings.max_pixels, settings.max_image_side)

    started = time.perf_counter()
    engine = _state["engine"]
    faces = await _run_in_thread(engine.analyse, img, scale)
    elapsed_ms = int((time.perf_counter() - started) * 1000)

    h, w = img.shape[:2]
    # Log only counts and timings — never embeddings or image content.
    log.info("embed: faces=%d size=%dx%d elapsed_ms=%d", len(faces), w, h, elapsed_ms)
    return {
        "model": engine.model_name,
        "imageWidth": int(round(w / scale)),
        "imageHeight": int(round(h / scale)),
        "elapsedMs": elapsed_ms,
        "faces": [
            {
                "bbox": f.bbox,
                "detScore": f.det_score,
                "width": f.width,
                "height": f.height,
                "embedding": f.embedding,
            }
            for f in faces
        ],
    }


async def _run_in_thread(fn, *args):
    from starlette.concurrency import run_in_threadpool
    return await run_in_threadpool(fn, *args)
