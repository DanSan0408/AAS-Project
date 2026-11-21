"""Imported first by every test module: tests must never send a real Telegram message, even on a machine whose .env has a bot token."""
from app import alerts

alerts.alerter.enabled = False
