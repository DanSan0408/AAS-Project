import json
import unittest
from unittest.mock import patch

import httpx

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app import alerts, llm
from app.config import settings

KEY = "sk-test-SECRET-KEY-VALUE"
URL = "https://api.deepseek.com/chat/completions"


def reply(content, status=200):
    body = {"choices": [{"message": {"content": content}}]} if status == 200 else {"error": {"message": "x"}}
    return httpx.Response(status, json=body, request=httpx.Request("POST", URL))


class DeepSeekCase(unittest.TestCase):
    def setUp(self):
        for name, value in dict(ai_provider="deepseek", deepseek_api_key=KEY, deepseek_model="deepseek-flash",
                                deepseek_base_url="https://api.deepseek.com", deepseek_thinking="disabled").items():
            patcher = patch.object(settings, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        sleeper = patch.object(llm.time, "sleep")
        sleeper.start()
        self.addCleanup(sleeper.stop)


class ProviderChoiceTest(unittest.TestCase):
    def pick(self, provider, deepseek_key):
        with patch.object(settings, "ai_provider", provider), patch.object(settings, "deepseek_api_key", deepseek_key):
            return llm.active_provider()

    def test_auto_uses_deepseek_when_it_has_a_key_and_gemini_otherwise(self):
        self.assertEqual("deepseek", self.pick("auto", "sk-x"))
        self.assertEqual("gemini", self.pick("auto", ""))

    def test_an_explicit_choice_wins(self):
        self.assertEqual("gemini", self.pick("gemini", "sk-x"))
        self.assertEqual("deepseek", self.pick("DeepSeek", ""))

    def test_an_unknown_value_falls_back_to_auto(self):
        self.assertEqual("deepseek", self.pick("nonsense", "sk-x"))


class RequestTest(DeepSeekCase):
    def call(self, system="Reply with JSON.", user="hello", temperature=0.2):
        with patch.object(llm.httpx, "post", return_value=reply('{"ok": true}')) as post:
            out = llm.invoke_json(system, user, temperature)
        return out, post

    def test_the_request_asks_for_json_without_thinking_and_carries_the_key(self):
        out, post = self.call()

        self.assertEqual({"ok": True}, out)
        args, kwargs = post.call_args
        self.assertEqual(URL, args[0])
        self.assertEqual("Bearer " + KEY, kwargs["headers"]["Authorization"])
        payload = kwargs["json"]
        self.assertEqual("deepseek-flash", payload["model"])
        self.assertEqual({"type": "json_object"}, payload["response_format"])
        self.assertEqual({"type": "disabled"}, payload["thinking"])
        self.assertEqual(0.2, payload["temperature"])
        self.assertEqual(["system", "user"], [m["role"] for m in payload["messages"]])

    def test_the_prompt_always_says_json_because_the_api_requires_it(self):
        _, post = self.call(system="Classify the comment.", user="text")
        sent = " ".join(m["content"] for m in post.call_args[1]["json"]["messages"]).lower()
        self.assertIn("json", sent)

    def test_with_thinking_on_no_temperature_is_sent(self):
        with patch.object(settings, "deepseek_thinking", "enabled"):
            _, post = self.call()
        payload = post.call_args[1]["json"]
        self.assertEqual({"type": "enabled"}, payload["thinking"])
        self.assertNotIn("temperature", payload)


class FailureTest(DeepSeekCase):
    def test_a_blank_reply_is_retried_once_then_given_up(self):
        with patch.object(llm.httpx, "post", side_effect=[reply("   "), reply("")]) as post, patch.object(alerts, "alert"):
            with self.assertRaises(llm.AiUnavailable):
                llm.invoke_json("Reply with JSON.", "x")
        self.assertEqual(2, post.call_count)

    def test_a_blank_reply_that_works_the_second_time_is_used(self):
        with patch.object(llm.httpx, "post", side_effect=[reply(""), reply('{"a": 1}')]):
            self.assertEqual({"a": 1}, llm.invoke_json("Reply with JSON.", "x"))

    def test_a_server_error_is_retried(self):
        with patch.object(llm.httpx, "post", side_effect=[reply(None, 503), reply('{"a": 2}')]) as post:
            self.assertEqual({"a": 2}, llm.invoke_json("Reply with JSON.", "x"))
        self.assertEqual(2, post.call_count)

    def test_payment_required_is_not_retried_and_the_alert_names_the_status_but_not_the_key_or_text(self):
        with patch.object(llm.httpx, "post", return_value=reply(None, 402)) as post, patch.object(alerts, "alert") as alert:
            with self.assertRaises(llm.AiUnavailable):
                llm.invoke_json("Reply with JSON.", "student private text")
        self.assertEqual(1, post.call_count)
        kind, text = alert.call_args[0]
        self.assertEqual("deepseek:HTTP402", kind)
        self.assertIn("402", text)
        self.assertNotIn(KEY, text)
        self.assertNotIn("student private text", text)

    def test_a_network_failure_becomes_ai_unavailable(self):
        with patch.object(llm.httpx, "post", side_effect=httpx.ConnectError("boom")), patch.object(alerts, "alert"):
            with self.assertRaises(llm.AiUnavailable):
                llm.invoke_json("Reply with JSON.", "x")

    def test_a_missing_key_is_unavailable_not_a_crash(self):
        with patch.object(settings, "deepseek_api_key", ""), patch.dict("os.environ", {}, clear=False), \
                patch.object(alerts, "alert"):
            with patch.object(llm.os.environ, "get", return_value=""):
                with self.assertRaises(llm.AiUnavailable):
                    llm.invoke_json("Reply with JSON.", "x")


if __name__ == "__main__":
    unittest.main()
