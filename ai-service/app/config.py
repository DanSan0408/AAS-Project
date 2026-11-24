import os
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # Which model provider answers: "deepseek", "gemini", or "auto" (DeepSeek when DEEPSEEK_API_KEY is set, otherwise Gemini).
    ai_provider: str = "auto"

    # DeepSeek (OpenAI-compatible API). Thinking is off by default: the answers here are short JSON, so it only adds delay and cost.
    deepseek_api_key: str = ""
    deepseek_model: str = "deepseek-flash"
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_thinking: str = "disabled"          # "disabled" or "enabled"

    # Optional price overrides for the cost estimate, JSON: {"model": {"hit": 0.1, "miss": 0.3, "out": 2.5}} in US dollars per million
    # tokens (a number, or [off-peak, peak]). DeepSeek's prices are built in; set this for Gemini or when a price changes.
    ai_prices_json: str = ""

    # Gemini API Configuration
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.8-flash"
    # Gemini 3+ only: "minimal", "low", "medium" or "high". Empty keeps Google's default for the model.
    gemini_thinking_level: str = ""

    # Microservice Security. No default: outside debug mode the service refuses to start without a strong secret.
    ai_internal_secret: str = ""
    
    # Telegram alerts: the same bot and chat as the Spring Boot app, so one chat shows everything.
    telegram_bot_token: str = ""
    telegram_alert_chat_id: str = ""
    telegram_deploy_chat_id: str = ""          # the alert chat falls back to this, as in the app
    adproject_environment_label: str = "production"
    ai_alerts_enabled: bool = True

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

