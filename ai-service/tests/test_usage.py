import json
import unittest
from datetime import datetime, timezone
from unittest.mock import patch

import httpx
from fastapi.testclient import TestClient

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app import llm, main, usage
from app.config import settings

KEY = "sk-test-SECRET-KEY-VALUE"
URL = "https://api.deepseek.com/chat/completions"
SECRET = "a-long-random-shared-secret-123"
MONDAY_NOON = datetime(2026, 11, 23, 12, 0, tzinfo=timezone.utc)      # off-peak
MONDAY_0200 = datetime(2026, 11, 23, 2, 0, tzinfo=timezone.utc)       # peak


def at(day, hour):
    return datetime(2026, 11, day, hour, 0, tzinfo=timezone.utc)


def reply(content, usage_block=None, status=200):
    body = {"choices": [{"message": {"content": content}}]}
    if usage_block is not None:
        body["usage"] = usage_block
    if status != 200:
        body = {"error": {"message": "x"}}
    return httpx.Response(status, json=body, request=httpx.Request("POST", URL))


FIRST = {"prompt_tokens": 1000, "completion_tokens": 200, "total_tokens": 1200,
         "prompt_cache_hit_tokens": 400, "prompt_cache_miss_tokens": 600}


class PriceTest(unittest.TestCase):
    def setUp(self):
        patcher = patch.object(settings, "ai_prices_json", "")
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_peak_hours_are_weekday_mornings_utc_only(self):
        self.assertTrue(usage.is_peak(at(23, 1)))      # Monday 01:00
        self.assertFalse(usage.is_peak(at(23, 4)))     # 04:00 is off-peak
        self.assertTrue(usage.is_peak(at(23, 6)))
        self.assertTrue(usage.is_peak(at(23, 9)))
        self.assertFalse(usage.is_peak(at(23, 10)))
        self.assertFalse(usage.is_peak(at(21, 2)))     # Saturday
        self.assertFalse(usage.is_peak(at(22, 7)))     # Sunday

    def test_a_cost_is_the_cache_hit_miss_and_output_parts_at_the_time_of_day_rate(self):
        off = usage.estimate_cost("deepseek-flash", 1000, 400, 200, MONDAY_NOON)
        self.assertAlmostEqual((400 * 0.003 + 600 * 0.15 + 200 * 0.6) / 1e6, off, places=12)
        peak = usage.estimate_cost("deepseek-flash", 1000, 400, 200, MONDAY_0200)
        self.assertAlmostEqual(off * 2, peak, places=12)

    def test_cache_hits_are_never_more_than_the_prompt_and_never_negative(self):
        self.assertAlmostEqual(usage.estimate_cost("deepseek-flash", 100, 100, 0, MONDAY_NOON),
                               usage.estimate_cost("deepseek-flash", 100, 5000, 0, MONDAY_NOON))
        self.assertEqual(0.0, usage.estimate_cost("deepseek-flash", 0, -5, -5, MONDAY_NOON))

    def test_a_model_without_a_price_has_no_cost_rather_than_a_wrong_one(self):
        self.assertIsNone(usage.estimate_cost("gemini-3.8-flash", 1000, 0, 100, MONDAY_NOON))

    def test_the_legacy_flash_name_costs_the_same(self):
        self.assertEqual(usage.estimate_cost("deepseek-flash", 10, 0, 10, MONDAY_NOON),
                         usage.estimate_cost("DeepSeek-V4-Flash", 10, 0, 10, MONDAY_NOON))

    def test_overrides_add_a_model_and_a_single_number_means_the_same_price_at_all_hours(self):
        with patch.object(settings, "ai_prices_json", '{"gemini-3.8-flash": {"hit": 0.1, "miss": 0.3, "out": [2.0, 2.0]}}'):
            cost = usage.estimate_cost("gemini-3.8-flash", 1_000_000, 0, 1_000_000, MONDAY_0200)
        self.assertAlmostEqual(0.3 + 2.0, cost, places=9)

    def test_a_bad_override_is_ignored_and_the_built_in_prices_remain(self):
        for bad in ("not json", "[1,2]", '{"x": {"hit": -1, "miss": 1, "out": 1}}', '{"x": {"hit": 1}}', '{"x": 5}'):
            with patch.object(settings, "ai_prices_json", bad):
                self.assertIsNone(usage.estimate_cost("x", 10, 0, 10, MONDAY_NOON), bad)
                self.assertIsNotNone(usage.estimate_cost("deepseek-flash", 10, 0, 10, MONDAY_NOON), bad)


class DeepSeekCase(unittest.TestCase):
    def setUp(self):
        for name, value in dict(ai_provider="deepseek", deepseek_api_key=KEY, deepseek_model="deepseek-flash",
                                deepseek_base_url="https://api.deepseek.com", deepseek_thinking="disabled",
                                ai_prices_json="").items():
            patcher = patch.object(settings, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        sleeper = patch.object(llm.time, "sleep")
        sleeper.start()
        self.addCleanup(sleeper.stop)


class TokenCountingTest(DeepSeekCase):
    def run_call(self, responses):
        with usage.track() as used, patch.object(llm.httpx, "post", side_effect=responses):
            try:
                llm.invoke_json("Reply with JSON.", "hi")
            except llm.AiUnavailable:
                pass
        return used

    def test_one_good_call_counts_its_tokens_and_the_cache_hits(self):
        used = self.run_call([reply('{"ok": true}', FIRST)])
        self.assertEqual((1, 0, 0), (used.calls, used.failed_calls, used.retries))
        self.assertEqual((1000, 400, 200), (used.prompt_tokens, used.cache_hit_tokens, used.completion_tokens))
        self.assertEqual("deepseek", used.provider)
        self.assertEqual("deepseek-flash", used.model)
        self.assertGreater(used.cost_usd, 0)

    def test_the_cache_counts_are_read_from_either_place_the_documentation_puts_them(self):
        nested = {"prompt_tokens": 500, "completion_tokens": 10, "prompt_tokens_details": {"prompt_cache_hit_tokens": 300}}
        self.assertEqual(300, self.run_call([reply('{"ok": true}', nested)]).cache_hit_tokens)
        cached_only = {"prompt_tokens": 500, "completion_tokens": 10, "prompt_tokens_details": {"cached_tokens": 120}}
        self.assertEqual(120, self.run_call([reply('{"ok": true}', cached_only)]).cache_hit_tokens)

    def test_a_blank_reply_that_is_retried_is_billed_twice(self):
        used = self.run_call([reply("   ", FIRST), reply('{"ok": true}', FIRST)])
        self.assertEqual(2000, used.prompt_tokens)
        self.assertEqual(400, used.completion_tokens)
        self.assertEqual((1, 0, 1), (used.calls, used.failed_calls, used.retries))

    def test_a_refused_call_is_a_failed_call_with_only_the_kind_of_error(self):
        used = self.run_call([reply("", status=402)])
        self.assertEqual((1, 1), (used.calls, used.failed_calls))
        self.assertEqual(["HTTP402"], used.errors)
        self.assertEqual(0, used.prompt_tokens)

    def test_a_reply_that_is_not_json_still_counts_what_it_cost(self):
        used = self.run_call([reply("sorry, no json", FIRST)])
        self.assertEqual(1, used.failed_calls)
        self.assertEqual(1000, used.prompt_tokens)
        self.assertEqual(["ValueError"], used.errors)

    def test_a_missing_or_odd_usage_block_never_breaks_the_call(self):
        for odd in (None, {}, "text", {"prompt_tokens": "many", "completion_tokens": True}, {"prompt_tokens": -5}):
            used = self.run_call([reply('{"ok": true}', odd)])
            self.assertEqual((1, 0), (used.calls, used.failed_calls), str(odd))
            self.assertEqual(0, used.prompt_tokens, str(odd))

    def test_nothing_is_collected_outside_a_request_and_nothing_breaks(self):
        with patch.object(llm.httpx, "post", return_value=reply('{"ok": true}', FIRST)):
            self.assertEqual({"ok": True}, llm.invoke_json("Reply with JSON.", "hi"))

    def test_a_request_with_no_model_call_sends_no_header(self):
        with usage.track() as used:
            pass
        self.assertIsNone(used.header_value())

    def test_the_header_is_compact_ascii_json_with_no_text_of_the_request(self):
        used = self.run_call([reply('{"ok": true}', FIRST)])
        value = used.header_value()
        value.encode("ascii")
        data = json.loads(value)
        self.assertEqual({"provider", "model", "calls", "failedCalls", "retries", "promptTokens", "cachedPromptTokens",
                          "completionTokens", "reasoningTokens", "modelMs", "costUsd", "errors"}, set(data))
        self.assertNotIn(KEY, value)

    def test_an_unpriced_model_reports_tokens_but_no_cost(self):
        with patch.object(settings, "deepseek_model", "some-new-model"):
            used = self.run_call([reply('{"ok": true}', FIRST)])
        self.assertEqual(1000, used.prompt_tokens)
        self.assertIsNone(json.loads(used.header_value())["costUsd"])


class GeminiUsageTest(unittest.TestCase):
    def test_langchain_usage_is_read_including_cached_and_reasoning_parts(self):
        with usage.track() as used:
            call = usage.start_call("gemini", "gemini-3.8-flash")
            usage.add_langchain_usage({"input_tokens": 300, "output_tokens": 80, "total_tokens": 380,
                                       "input_token_details": {"cache_read": 100}, "output_token_details": {"reasoning": 30}})
            usage.finish_call(call, ok=True)
        self.assertEqual((300, 100, 80, 30), (used.prompt_tokens, used.cache_hit_tokens, used.completion_tokens, used.reasoning_tokens))

    def test_anything_that_is_not_a_dict_is_ignored(self):
        with usage.track() as used:
            call = usage.start_call("gemini", "gemini-3.8-flash")
            usage.add_langchain_usage(object())
            usage.finish_call(call, ok=True)
        self.assertEqual(0, used.prompt_tokens)


class EndToEndTest(DeepSeekCase):
    """A real request through the real endpoint and graph: the answer is unchanged and the usage rides in a header."""

    def setUp(self):
        super().setUp()
        for p in (patch.object(main.settings, "ai_internal_secret", SECRET), patch.object(main.settings, "debug", False)):
            p.start()
            self.addCleanup(p.stop)
        self.client = TestClient(main.app)

    def check(self, comments):
        return self.client.post("/api/v1/comments/check", json={"comments": comments}, headers={"X-Internal-Secret": SECRET})

    def test_a_comment_check_reports_what_it_used_without_changing_the_body(self):
        answer = json.dumps({"results": [{"id": "1", "label": "ok", "confidence": 0.9, "language": "en", "reason": "fine"}]})
        with patch.object(llm.httpx, "post", return_value=reply(answer, FIRST)):
            response = self.check([{"id": "1", "text": "Good teamwork and a clear report."}])
        self.assertEqual(200, response.status_code)
        self.assertEqual("OK", response.json()["aiStatus"])
        self.assertNotIn("usage", response.json())
        header = json.loads(response.headers["X-AI-Usage"])
        self.assertEqual((1, 0, 1000, 400, 200), (header["calls"], header["failedCalls"], header["promptTokens"],
                                                  header["cachedPromptTokens"], header["completionTokens"]))
        self.assertEqual("deepseek-flash", header["model"])
        self.assertGreater(header["costUsd"], 0)

    def test_a_failed_model_call_is_reported_and_the_answer_still_falls_back(self):
        with patch.object(llm.httpx, "post", return_value=reply("", status=402)):
            response = self.check([{"id": "1", "text": "Good teamwork and a clear report."}])
        self.assertEqual("UNAVAILABLE", response.json()["aiStatus"])
        header = json.loads(response.headers["X-AI-Usage"])
        self.assertEqual((1, 1, ["HTTP402"]), (header["calls"], header["failedCalls"], header["errors"]))

    def test_a_request_that_never_reaches_the_model_has_no_usage_header(self):
        with patch.object(llm.httpx, "post") as post:
            response = self.check([{"id": "1", "text": ""}])
        post.assert_not_called()
        self.assertNotIn("X-AI-Usage", response.headers)

    def test_two_requests_do_not_share_their_numbers(self):
        answer = json.dumps({"results": [{"id": "1", "label": "ok", "confidence": 0.9, "language": "en", "reason": "fine"}]})
        with patch.object(llm.httpx, "post", return_value=reply(answer, FIRST)):
            first = self.check([{"id": "1", "text": "Good teamwork and a clear report."}])
            second = self.check([{"id": "1", "text": "Good teamwork and a clear report."}])
        self.assertEqual(json.loads(first.headers["X-AI-Usage"])["promptTokens"], json.loads(second.headers["X-AI-Usage"])["promptTokens"])


class BalanceTest(DeepSeekCase):
    def get(self, response=None, error=None):
        with patch.object(llm.httpx, "get", return_value=response, side_effect=error) as get:
            return llm.provider_balance(), get

    def test_the_balance_is_read_with_the_key_and_returned_as_numbers(self):
        body = {"is_available": True, "balance_infos": [{"currency": "USD", "total_balance": "12.50",
                                                           "granted_balance": "2.50", "topped_up_balance": "10.00"}]}
        out, get = self.get(httpx.Response(200, json=body, request=httpx.Request("GET", "x")))
        self.assertEqual("https://api.deepseek.com/user/balance", get.call_args[0][0])
        self.assertEqual("Bearer " + KEY, get.call_args[1]["headers"]["Authorization"])
        self.assertTrue(out["supported"] and out["available"])
        self.assertEqual([{"currency": "USD", "total": 12.5, "granted": 2.5, "toppedUp": 10.0}], out["balances"])
        self.assertNotIn(KEY, json.dumps(out))

    def test_a_refusal_or_a_network_failure_is_reported_as_a_kind_never_raised(self):
        out, _ = self.get(httpx.Response(401, json={"error": "bad key sk-secret"}, request=httpx.Request("GET", "x")))
        self.assertEqual("HTTP 401", out["error"])
        self.assertNotIn("sk-secret", json.dumps(out))
        out, _ = self.get(error=httpx.ConnectError("down"))
        self.assertEqual("ConnectError", out["error"])

    def test_junk_amounts_become_none_not_errors(self):
        body = {"balance_infos": [{"currency": "USD", "total_balance": "n/a", "granted_balance": None, "topped_up_balance": "inf"}]}
        out, _ = self.get(httpx.Response(200, json=body, request=httpx.Request("GET", "x")))
        self.assertEqual([{"currency": "USD", "total": None, "granted": None, "toppedUp": None}], out["balances"])

    def test_gemini_has_no_balance_call(self):
        with patch.object(settings, "ai_provider", "gemini"):
            self.assertEqual({"provider": "gemini", "supported": False, "available": None, "balances": [], "error": None},
                             llm.provider_balance())

    def test_the_balance_endpoint_needs_the_secret(self):
        with patch.object(main.settings, "ai_internal_secret", SECRET), patch.object(main.settings, "debug", False):
            client = TestClient(main.app)
            self.assertEqual(401, client.get("/api/v1/usage/balance").status_code)
            with patch.object(llm.httpx, "get", side_effect=httpx.ConnectError("down")):
                ok = client.get("/api/v1/usage/balance", headers={"X-Internal-Secret": SECRET})
        self.assertEqual(200, ok.status_code)
        self.assertEqual("ConnectError", ok.json()["error"])


class TypeSafeJevUsageTest(unittest.TestCase):
    def test_jev_pricing_charges_input_only_and_zero_for_output(self):
        cost = usage.estimate_cost("jev-1.14.0", 1_000_000, 0, 500_000, MONDAY_NOON)
        self.assertAlmostEqual(0.042, cost, places=9)

    def test_invoke_system_one_tracks_tokens_and_cost(self):
        body = {
            "model": "jev-1.14.0",
            "answers": {"moderation": {"type": "choice", "choice": "ok", "confidence": 0.94}},
            "usage": {"input_tokens": 250, "output_tokens": 12},
        }
        resp = httpx.Response(200, json=body, request=httpx.Request("POST", "https://api.typesafe.ai/v1/systemone"))
        with patch.object(settings, "typesafe_api_key", "ts-secret-key"), \
                patch.object(settings, "typesafe_enabled", "auto"), \
                patch.object(settings, "typesafe_model", "jev-latest"), \
                patch.object(settings, "typesafe_base_url", "https://api.typesafe.ai"), \
                patch.object(llm.httpx, "post", return_value=resp), \
                usage.track() as used:
            answers = llm.invoke_system_one("Good job", {"moderation": {"type": "choice"}})

        self.assertEqual("ok", answers["moderation"]["choice"])
        self.assertEqual("typesafe", used.provider)
        self.assertEqual("jev-1.14.0", used.model)
        self.assertEqual(250, used.prompt_tokens)
        self.assertAlmostEqual(250 * 0.042 / 1_000_000, used.cost_usd, places=12)


if __name__ == "__main__":
    unittest.main()
