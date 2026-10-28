# ---- Face service: FastAPI + InsightFace (CPU) ----
FROM python:3.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    FACE_MODEL_ROOT=/models

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential libgl1 libglib2.0-0 curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && apt-get purge -y build-essential && apt-get autoremove -y

# Pre-download the model pack at build time so the service runs fully offline.
# Override with --build-arg FACE_MODEL_PACK=buffalo_s for smaller/faster models.
ARG FACE_MODEL_PACK=buffalo_l
ENV FACE_MODEL_PACK=${FACE_MODEL_PACK}
RUN python -c "from insightface.app import FaceAnalysis; \
FaceAnalysis(name='${FACE_MODEL_PACK}', root='/models', allowed_modules=['detection','recognition'], providers=['CPUExecutionProvider'])"

COPY app ./app

RUN useradd --system --uid 10001 face && chown -R face /models /srv
USER face

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl -fs http://localhost:8000/health || exit 1
# FACE_WORKERS = number of model copies (each uses ~1 GB RAM). Raise on servers with many cores.
ENV FACE_WORKERS=1
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers ${FACE_WORKERS}"]
