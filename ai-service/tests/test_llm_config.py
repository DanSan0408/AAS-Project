import unittest
from unittest.mock import patch

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app import llm
from app.config import Settings, settings


class ModelSettingsTest(unittest.TestCase):
    """Gemini 3 and later ignore the sampling settings and will start refusing them, so they are only sent to older models."""

    def build(self, model, level=""):
        captured = {}

        class Spy:
            def __init__(self, **kwargs):
                captured.update(kwargs)

        with patch.object(llm, "ChatGoogleGenerativeAI", Spy), \
                patch.object(settings, "gemini_model", model), \
                patch.object(settings, "gemini_api_key", "test-key"), \
                patch.object(settings, "gemini_thinking_level", level):
            llm.get_gemini_llm(0.3)
        return captured

    def test_the_default_model_is_not_a_retired_one(self):
        default = Settings.model_fields["gemini_model"].default  # not the live setting, which a local .env may override
        self.assertNotIn("2.5", default)
        self.assertTrue(default.startswith("gemini-3"))

    def test_a_gemini_3_model_is_not_sent_a_temperature(self):
        for name in ("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3-flash-preview", "gemini-4-hydrogen"):
            self.assertNotIn("temperature", self.build(name), name)

    def test_an_older_model_still_gets_the_temperature(self):
        self.assertEqual(0.3, self.build("gemini-2.5-flash-lite")["temperature"])

    def test_the_thinking_level_is_only_sent_when_one_is_configured(self):
        self.assertNotIn("thinking_level", self.build("gemini-3.8-flash"))
        self.assertEqual("low", self.build("gemini-3.8-flash", "low")["thinking_level"])
        self.assertNotIn("thinking_level", self.build("gemini-2.5-flash-lite", "low"))

    def test_there_is_room_for_the_answer_after_the_model_has_thought(self):
        self.assertGreaterEqual(self.build("gemini-3.8-flash")["max_output_tokens"], 8192)


if __name__ == "__main__":
    unittest.main()
