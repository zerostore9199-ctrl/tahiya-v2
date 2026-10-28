"""Safe image decoding: validates bytes before anything reaches the model."""
from __future__ import annotations

import cv2
import numpy as np

# Magic numbers for the formats we accept.
_SIGNATURES = (
    b"\xff\xd8\xff",          # JPEG
    b"\x89PNG\r\n\x1a\n",     # PNG
)


class ImageError(ValueError):
    """Raised when an uploaded file is not an acceptable image."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def _is_webp(data: bytes) -> bool:
    return len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP"


def decode_image(data: bytes, max_bytes: int, max_pixels: int, max_side: int) -> tuple[np.ndarray, float]:
    """Decode an uploaded image into a BGR array.

    Returns the (possibly downscaled) image and the scale factor that was applied,
    so that bounding boxes can be mapped back to the original coordinates.
    """
    if not data:
        raise ImageError("EMPTY_FILE", "The uploaded file is empty")
    if len(data) > max_bytes:
        raise ImageError("FILE_TOO_LARGE", f"Image exceeds {max_bytes} bytes")
    if not (any(data.startswith(sig) for sig in _SIGNATURES) or _is_webp(data)):
        raise ImageError("UNSUPPORTED_FORMAT", "Only JPEG, PNG and WebP images are accepted")

    buf = np.frombuffer(data, dtype=np.uint8)
    # IMREAD_COLOR honours EXIF orientation, so phone photos come out upright.
    img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
    if img is None:
        raise ImageError("CORRUPT_IMAGE", "The image could not be decoded")

    h, w = img.shape[:2]
    if h * w > max_pixels:
        raise ImageError("IMAGE_TOO_LARGE", "Image resolution is too large")
    if min(h, w) < 32:
        raise ImageError("IMAGE_TOO_SMALL", "Image is too small")

    scale = 1.0
    longest = max(h, w)
    if longest > max_side:
        scale = max_side / float(longest)
        img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
    return img, scale
