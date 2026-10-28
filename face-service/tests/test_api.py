"""API tests with a fake engine (fast, no model download needed)."""
import os

import cv2
import numpy as np
import pytest
from fastapi.testclient import TestClient

os.environ.setdefault("FACE_API_KEY", "test-key-0123456789abcdef")

from app import main  # noqa: E402
from app.engine import DetectedFace  # noqa: E402

KEY = os.environ["FACE_API_KEY"]


class FakeEngine:
    model_name = "fake"

    def __init__(self):
        self.calls = 0

    def analyse(self, img, scale):
        self.calls += 1
        emb = [0.0] * 512
        emb[0] = 1.0
        return [DetectedFace(bbox=[10, 10, 110, 130], det_score=0.9, embedding=emb, width=100, height=120)]


@pytest.fixture()
def client():
    main._state["engine"] = FakeEngine()
    with TestClient(main.app) as c:
        yield c
    main._state.clear()


def _jpeg(w=200, h=200) -> bytes:
    img = np.full((h, w, 3), 127, dtype=np.uint8)
    ok, buf = cv2.imencode(".jpg", img)
    assert ok
    return buf.tobytes()


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "UP"


def test_embed_requires_api_key(client):
    r = client.post("/embed", files={"image": ("a.jpg", _jpeg(), "image/jpeg")})
    assert r.status_code == 401


def test_embed_rejects_wrong_api_key(client):
    r = client.post("/embed", headers={"X-API-Key": "wrong-key-wrong-key"},
                    files={"image": ("a.jpg", _jpeg(), "image/jpeg")})
    assert r.status_code == 401


def test_embed_returns_faces(client):
    r = client.post("/embed", headers={"X-API-Key": KEY},
                    files={"image": ("a.jpg", _jpeg(), "image/jpeg")})
    assert r.status_code == 200
    body = r.json()
    assert body["model"] == "fake"
    assert body["imageWidth"] == 200
    assert len(body["faces"]) == 1
    assert len(body["faces"][0]["embedding"]) == 512


def test_embed_rejects_non_image(client):
    r = client.post("/embed", headers={"X-API-Key": KEY},
                    files={"image": ("a.jpg", b"<?php echo 1; ?>", "image/jpeg")})
    assert r.status_code == 422
    assert r.json()["detail"]["code"] == "UNSUPPORTED_FORMAT"


def test_embed_rejects_corrupt_jpeg(client):
    r = client.post("/embed", headers={"X-API-Key": KEY},
                    files={"image": ("a.jpg", b"\xff\xd8\xff" + b"\x00" * 100, "image/jpeg")})
    assert r.status_code == 422
    assert r.json()["detail"]["code"] == "CORRUPT_IMAGE"


def test_embed_rejects_empty(client):
    r = client.post("/embed", headers={"X-API-Key": KEY},
                    files={"image": ("a.jpg", b"", "image/jpeg")})
    assert r.status_code == 422
    assert r.json()["detail"]["code"] == "EMPTY_FILE"
