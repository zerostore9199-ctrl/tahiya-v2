"""Face detection (SCRFD) and embedding (ArcFace) using InsightFace model packs."""
from __future__ import annotations

import logging
import threading
from dataclasses import dataclass

import numpy as np

from .config import Settings

log = logging.getLogger("face-service.engine")


@dataclass
class DetectedFace:
    bbox: list[int]            # [x1, y1, x2, y2] in original image coordinates
    det_score: float
    embedding: list[float]     # L2-normalised, 512 floats
    width: int
    height: int


class FaceEngine:
    """Thin wrapper around insightface.app.FaceAnalysis.

    Only the detection and recognition models are loaded (no age/gender/landmark
    3D models) to keep CPU cost and memory low.
    """

    BATCH = 32  # faces per ArcFace ONNX call

    def __init__(self, settings: Settings):
        from insightface.app import FaceAnalysis  # imported lazily: heavy import

        self.settings = settings
        self.model_name = settings.model_pack
        self._app = FaceAnalysis(
            name=settings.model_pack,
            root=settings.model_root,
            allowed_modules=["detection", "recognition"],
            providers=list(settings.providers),
        )
        self._app.prepare(ctx_id=-1, det_thresh=settings.det_threshold,
                          det_size=(settings.det_size, settings.det_size))
        self._sem = threading.BoundedSemaphore(max(1, settings.max_concurrency))
        log.info("Face engine ready: pack=%s det_size=%s providers=%s",
                 settings.model_pack, settings.det_size, settings.providers)

    def analyse(self, img: np.ndarray, scale: float) -> list[DetectedFace]:
        """Detect all faces, then embed them in batches (much faster than one ONNX call per face)."""
        from insightface.utils import face_align

        det = self._app.models["detection"]
        rec = self._app.models["recognition"]
        with self._sem:
            bboxes, kpss = det.detect(img, max_num=self.settings.max_faces, metric="default")
            keep: list[int] = []
            crops: list[np.ndarray] = []
            for i, box in enumerate(bboxes):
                w, h = (box[2] - box[0]) / scale, (box[3] - box[1]) / scale
                if min(w, h) < self.settings.min_face_px or kpss is None:
                    continue
                keep.append(i)
                crops.append(face_align.norm_crop(img, landmark=kpss[i], image_size=rec.input_size[0]))
            feats = np.zeros((0, 512), dtype=np.float32)
            for start in range(0, len(crops), self.BATCH):
                part = np.asarray(rec.get_feat(crops[start:start + self.BATCH]), dtype=np.float32)
                feats = np.vstack([feats, part]) if feats.size else part

        result: list[DetectedFace] = []
        inv = 1.0 / scale if scale else 1.0
        for j, i in enumerate(keep):
            x1, y1, x2, y2 = (float(v) * inv for v in bboxes[i][:4])
            emb = feats[j] / (np.linalg.norm(feats[j]) + 1e-12)
            result.append(DetectedFace(
                bbox=[int(round(x1)), int(round(y1)), int(round(x2)), int(round(y2))],
                det_score=round(float(bboxes[i][4]), 4),
                embedding=[round(float(v), 6) for v in emb],
                width=int(round(x2 - x1)),
                height=int(round(y2 - y1)),
            ))
        result.sort(key=lambda d: (d.bbox[0], d.bbox[1]))
        return result
