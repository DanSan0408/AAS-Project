import logging

from fastapi import FastAPI, Header, HTTPException, Depends, status
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.schemas.team_dynamics_schemas import (
    TeamDynamicsRequest,
    TeamDynamicsResponse
)
from app.schemas.moderation_schemas import (
    ModerationBriefingRequest,
    ModerationBriefingResponse
)
from app.graphs.team_dynamics_graph import execute_team_dynamics_audit
from app.graphs.moderation_graph import execute_moderation_briefing

logging.basicConfig(level=logging.INFO if not settings.debug else logging.DEBUG)
logger = logging.getLogger("ai_service")

app = FastAPI(
    title="IAMS AI Microservice",
    description="LangGraph-powered AI service for Team Dynamics Audit & Moderation Analytics",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def verify_internal_secret(x_internal_secret: str = Header(default="", alias="X-Internal-Secret")):
    """Validates that internal requests from Spring Boot possess the configured secret."""
    expected_secret = settings.ai_internal_secret
    if expected_secret and x_internal_secret != expected_secret:
        logger.warning("Unauthorized access attempt: Invalid X-Internal-Secret")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-Internal-Secret header."
        )
    return True


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "service": "IAMS AI Service",
        "llm_provider": "gemini",
        "model": settings.gemini_model,
        "has_api_key": bool(settings.gemini_api_key)
    }


@app.post(
    "/api/v1/team-dynamics/audit",
    response_model=TeamDynamicsResponse,
    dependencies=[Depends(verify_internal_secret)]
)
def audit_team_dynamics(request: TeamDynamicsRequest):
    try:
        logger.info(f"Auditing team dynamics for group #{request.groupId}: '{request.groupName}' on assessment #{request.assessmentId}")
        return execute_team_dynamics_audit(request)
    except Exception as e:
        logger.error(f"Error executing team dynamics audit: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@app.post(
    "/api/v1/moderation/briefing",
    response_model=ModerationBriefingResponse,
    dependencies=[Depends(verify_internal_secret)]
)
def generate_moderation_briefing(request: ModerationBriefingRequest):
    try:
        logger.info(f"Generating moderation briefing for assessment #{request.assessmentId}: '{request.assessmentTitle}'")
        return execute_moderation_briefing(request)
    except Exception as e:
        logger.error(f"Error generating moderation briefing: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))
