import os
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # Gemini API Configuration
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash"

    # Microservice Security
    ai_internal_secret: str = "dev-insecure-secret-change-in-env"
    
    # Service Configuration
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = False

    model_config = SettingsConfigDict(
        env_file=os.path.join(os.path.dirname(__file__), "..", "..", "adproject", ".env"),
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()

