"""Integration test against the real InsightFace models.

Skipped automatically when the model pack is not available locally.
Run with:  FACE_RUN_MODEL_TESTS=1 pytest tests/test_real_model.py
"""
import os
import pathlib

import cv2
import numpy as np
import pytest

RUN = os.getenv("FACE_RUN_MODEL_TESTS") == "1"
pytestmark = pytest.mark.skipif(not RUN, reason="set FACE_RUN_MODEL_TESTS=1 to run model tests")


@pytest.fixture(scope="module")
def engine():
    os.environ.setdefault("FACE_API_KEY", "test-key-0123456789abcdef")
    from app.config import Settings
    from app.engine import FaceEngine
    return FaceEngine(Settings.from_env())


@pytest.fixture(scope="module")
def group_image():
    import insightface
    path = pathlib.Path(insightface.__file__).parent / "data" / "images" / "t1.jpg"
    img = cv2.imread(str(path))
    assert img is not None, path
    return img


def _cos(a, b):
    a, b = np.asarray(a), np.asarray(b)
    return float(a @ b / (np.linalg.norm(a) * np.linalg.norm(b)))


def test_detects_every_face_in_group_photo(engine, group_image):
    faces = engine.analyse(group_image, 1.0)
    assert len(faces) >= 5
    for f in faces:
        assert len(f.embedding) == 512
        assert abs(np.linalg.norm(f.embedding) - 1.0) < 1e-3


def test_same_person_matches_under_photometric_changes(engine, group_image):
    """Crop each face, alter brightness/scale/flip, and check it re-identifies.

    This validates the pipeline end to end; it is NOT an accuracy benchmark.
    """
    faces = engine.analyse(group_image, 1.0)
    h, w = group_image.shape[:2]
    # Variant of the whole photo: brighter, slightly smaller and mirrored.
    variant = cv2.convertScaleAbs(group_image, alpha=1.15, beta=20)
    variant = cv2.resize(variant, (int(w * 0.85), int(h * 0.85)))
    variant = cv2.flip(variant, 1)
    v_faces = engine.analyse(variant, 1.0)
    assert len(v_faces) == len(faces)

    for f in faces:
        sims = sorted((_cos(f.embedding, v.embedding) for v in v_faces), reverse=True)
        assert sims[0] > 0.6, sims[:2]            # same person is a strong match
        assert sims[1] < 0.35, sims[:2]           # nobody else comes close


def test_different_people_are_far_apart(engine, group_image):
    faces = engine.analyse(group_image, 1.0)
    for i in range(len(faces)):
        for j in range(i + 1, len(faces)):
            assert _cos(faces[i].embedding, faces[j].embedding) < 0.4
