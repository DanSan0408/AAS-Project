FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# Run as an unprivileged user: the service only reads its own code and calls out to the model.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin aiuser
USER aiuser

EXPOSE 8000

ENV PYTHONUNBUFFERED=1

# /health is open by design and says nothing but "healthy".
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

