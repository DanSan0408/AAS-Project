import hmac
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Depends, Response, status

from app import alerts, llm, usage
from app.config import settings
from app.schemas.team_dynamics_schemas import (
    TeamDynamicsRequest,
    TeamDynamicsResponse
)
from app.schemas.moderation_schemas import (
    ModerationBriefingRequest,
    ModerationBriefingResponse
)
from app.schemas.comment_schemas import CheckCommentsRequest, CheckCommentsResponse
from app.graphs.comment_check import check_comments
from app.graphs.team_dynamics_graph import execute_team_dynamics_audit
from app.graphs.moderation_graph import execute_moderation_briefing

logging.basicConfig(level=logging.INFO if not settings.debug else logging.DEBUG)
logger = logging.getLogger("ai_service")

KNOWN_DEV_SECRETS = {"", "changeme", "dev-insecure-secret-change-in-env", "dev-internal-secret-token-change-in-env"}
MIN_SECRET_LENGTH = 16


def validate_security(secret: str, debug: bool) -> None:
    """Refuses to run without a real shared secret (debug mode, for local development, is the only exception).

    An empty or well-known secret would let anyone who can reach this port spend the AI quota.
    """
    if debug:
        return
    lowered = secret.lower()
    if secret in KNOWN_DEV_SECRETS or len(secret) < MIN_SECRET_LENGTH or "change" in lowered or lowered.startswith("dev-"):
        raise RuntimeError(
            f"AI_INTERNAL_SECRET must be set to a random value of at least {MIN_SECRET_LENGTH} characters "
            "(it must match the app's AI_INTERNAL_SECRET). For local development only, set DEBUG=true to skip this check."
        )


@asynccontextmanager
async def lifespan(_app: FastAPI):
    try:
        validate_security(settings.ai_internal_secret, settings.debug)
    except RuntimeError:
        alerts.alert("startup-refused", "[ERROR] The AI service refused to start: AI_INTERNAL_SECRET is missing, short or a placeholder.")
        raise
    alerts.alert("startup", "The AI service started and is accepting requests.")
    yield
    alerts.alert("shutdown", "The AI service is shutting down.")


app = FastAPI(
    title="IAMS AI Microservice",
    description="LangGraph-powered AI service for Team Dynamics Audit & Moderation Analytics",
    version="1.0.0",
    lifespan=lifespan,
)
# Only the Spring Boot app calls this service, server to server, so no CORS is offered to browsers.


def verify_internal_secret(x_internal_secret: str = Header(default="", alias="X-Internal-Secret")):
    """Validates that internal requests from Spring Boot possess the configured secret (constant-time comparison)."""
    expected = settings.ai_internal_secret
    if not expected:
        if settings.debug:
            return True   # local development without a secret
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or missing X-Internal-Secret header.")
    if not hmac.compare_digest(x_internal_secret.encode("utf-8"), expected.encode("utf-8")):
        logger.warning("Unauthorized access attempt: Invalid X-Internal-Secret")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or missing X-Internal-Secret header.")
    return True


USAGE_HEADER = "X-AI-Usage"


def _report_usage(response: Response, used: usage.RequestUsage) -> None:
    """Tells the app what the request cost, in a header, so the response bodies stay as they were."""
    value = used.header_value()
    if value:
        response.headers[USAGE_HEADER] = value


@app.get("/health")
def health():
    return {"status": "healthy", "service": "IAMS AI Service"}


@app.post(
    "/api/v1/team-dynamics/audit",
    response_model=TeamDynamicsResponse,
    dependencies=[Depends(verify_internal_secret)]
)
def audit_team_dynamics(request: TeamDynamicsRequest, response: Response):
    try:
        logger.info(f"Auditing team dynamics for group #{request.groupId}: '{request.groupName}' on assessment #{request.assessmentId}")
        with usage.track() as used:
            result = execute_team_dynamics_audit(request)
        _report_usage(response, used)
        return result
    except Exception as e:
        logger.error(f"Error executing team dynamics audit: {e}", exc_info=True)
        alerts.alert("500:audit", f"[ERROR] The team dynamics audit failed ({type(e).__name__}).")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="The AI analysis could not be completed.")


@app.post(
    "/api/v1/moderation/briefing",
    response_model=ModerationBriefingResponse,
    dependencies=[Depends(verify_internal_secret)]
)
def generate_moderation_briefing(request: ModerationBriefingRequest, response: Response):
    try:
        logger.info(f"Generating moderation briefing for assessment #{request.assessmentId}: '{request.assessmentTitle}'")
        with usage.track() as used:
            result = execute_moderation_briefing(request)
        _report_usage(response, used)
        return result
    except Exception as e:
        logger.error(f"Error generating moderation briefing: {e}", exc_info=True)
        alerts.alert("500:briefing", f"[ERROR] The moderation briefing failed ({type(e).__name__}).")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="The AI analysis could not be completed.")


@app.post(
    "/api/v1/comments/check",
    response_model=CheckCommentsResponse,
    dependencies=[Depends(verify_internal_secret)]
)
def check_comments_endpoint(request: CheckCommentsRequest, response: Response):
    try:
        logger.info("Checking %d comments for moderation", len(request.comments))   # counts only: never the comment text
        with usage.track() as used:
            result = check_comments(request)
        _report_usage(response, used)
        return result
    except Exception as e:
        logger.error(f"Error checking comments: {type(e).__name__}", exc_info=True)
        alerts.alert("500:comments", f"[ERROR] The comment check failed ({type(e).__name__}).")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="The AI analysis could not be completed.")


@app.get("/api/v1/usage/balance", dependencies=[Depends(verify_internal_secret)])
def provider_balance():
    """What is left on the provider account, for the Super Admin AI usage page. Never raises; failures are in the body."""
    return llm.provider_balance()
