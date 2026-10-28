import os
from langchain_google_genai import ChatGoogleGenerativeAI
from app.config import settings


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
        max_retries=2
    )

