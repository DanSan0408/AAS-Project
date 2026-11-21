import logging
import os

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_google_genai import ChatGoogleGenerativeAI

from app import alerts
from app.analysis.text_safety import extract_json
from app.config import settings

logger = logging.getLogger("ai_service.llm")


class AiUnavailable(Exception):
    """The model could not give a usable answer. The message is safe to show to a user."""


def get_gemini_llm(temperature: float = 0.2) -> ChatGoogleGenerativeAI:
    """Returns a ChatGoogleGenerativeAI instance using the configured GEMINI_API_KEY and model."""
    api_key = settings.gemini_api_key or os.environ.get("GEMINI_API_KEY", "")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set. Please add it to your .env or environment variables.")

    return ChatGoogleGenerativeAI(
        model=settings.gemini_model,
        google_api_key=api_key,
        temperature=temperature,
        timeout=60,
        max_retries=2,
        max_output_tokens=4096,
    )


def invoke_json(system: str, user: str, temperature: float = 0.1) -> dict:
    """Asks the model and returns the JSON object in its reply.

    Raises AiUnavailable on any failure (no key, network, quota, a reply that is not JSON). Only the kind of error is logged:
    never the prompt, which contains student comments.
    """
    try:
        response = get_gemini_llm(temperature).invoke([SystemMessage(content=system), HumanMessage(content=user)])
        return extract_json(response.content)
    except Exception as exc:  # deliberately broad: whatever went wrong, the caller falls back to calculated results
        logger.warning("AI call failed (%s)", type(exc).__name__)
        # Only the kind of error: the message can hold a key or student text.
        alerts.alert("gemini:" + type(exc).__name__,
                     f"[ERROR] The Gemini call failed ({type(exc).__name__}). AI features are falling back to calculated results.")
        raise AiUnavailable("The AI did not return a usable answer.") from exc
