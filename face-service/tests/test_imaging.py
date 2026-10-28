import cv2
import numpy as np
import pytest

from app.imaging import ImageError, decode_image


def _png(w, h):
    ok, buf = cv2.imencode(".png", np.zeros((h, w, 3), dtype=np.uint8))
    assert ok
    return buf.tobytes()


def test_downscales_large_images_and_reports_scale():
    img, scale = decode_image(_png(4000, 1000), 50_000_000, 50_000_000, 2000)
    assert img.shape[1] == 2000
    assert scale == pytest.approx(0.5)


def test_rejects_oversized_file():
    with pytest.raises(ImageError) as e:
        decode_image(_png(100, 100), 10, 50_000_000, 2000)
    assert e.value.code == "FILE_TOO_LARGE"


def test_rejects_too_many_pixels():
    with pytest.raises(ImageError) as e:
        decode_image(_png(1000, 1000), 50_000_000, 999_999, 5000)
    assert e.value.code == "IMAGE_TOO_LARGE"


def test_rejects_tiny_images():
    with pytest.raises(ImageError) as e:
        decode_image(_png(10, 10), 50_000_000, 50_000_000, 5000)
    assert e.value.code == "IMAGE_TOO_SMALL"
