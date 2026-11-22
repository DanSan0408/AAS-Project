import unittest
from unittest.mock import patch

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app import alerts
from app.alerts import Alerter, COALESCE_SECONDS, MAX_PER_WINDOW


class Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


def make(sent, clock=None, **kwargs):
    return Alerter("123:TOKEN", "-100", "production", send=lambda text: sent.append(text) or True,
                   clock=clock or Clock(), background=False, **kwargs)


class AlerterTest(unittest.TestCase):
    def test_the_first_alert_of_a_kind_goes_out_at_once_with_the_label(self):
        sent = []
        make(sent).alert("gemini:X", "[ERROR] The Gemini call failed (X).")
        self.assertEqual(["[production] [ai-service] [ERROR] The Gemini call failed (X)."], sent)

    def test_repeats_are_counted_and_the_count_goes_out_with_the_next_alert(self):
        sent, clock = [], Clock()
        a = make(sent, clock)
        for _ in range(3):
            a.alert("k", "[ERROR] boom")
        self.assertEqual(1, len(sent))
        clock.now += COALESCE_SECONDS + 1
        a.alert("k", "[ERROR] boom")
        self.assertEqual(2, len(sent))
        self.assertIn("+2 more like this", sent[1])

    def test_repeats_with_no_follow_up_are_reported_by_the_flush(self):
        sent, clock = [], Clock()
        a = make(sent, clock)
        a.alert("k", "[ERROR] boom")
        a.alert("k", "[ERROR] boom")
        clock.now += COALESCE_SECONDS + 1
        a.flush()
        self.assertEqual(2, len(sent))
        self.assertIn("+1 more like this", sent[1])
        a.flush()
        self.assertEqual(2, len(sent), "nothing is reported twice")

    def test_different_kinds_are_all_sent(self):
        sent = []
        a = make(sent)
        a.alert("a", "one")
        a.alert("b", "two")
        self.assertEqual(2, len(sent))

    def test_a_storm_is_capped_and_the_next_message_says_how_many_were_held_back(self):
        sent, clock = [], Clock()
        a = make(sent, clock)
        for i in range(MAX_PER_WINDOW + 5):
            a.alert(f"kind{i}", "x")
        self.assertEqual(MAX_PER_WINDOW, len(sent))
        clock.now += 700
        a.alert("later", "y")
        self.assertIn("held back by the flood limit", sent[-1])

    def test_nothing_is_sent_when_unconfigured_or_switched_off(self):
        sent = []
        Alerter("", "", "production", send=sent.append, background=False).alert("k", "x")
        Alerter("t", "c", "production", enabled=False, send=sent.append, background=False).alert("k", "x")
        self.assertEqual([], sent)

    def test_a_failing_sender_never_breaks_the_caller(self):
        def boom(_text):
            raise RuntimeError("network down")
        Alerter("t", "c", "production", send=boom, background=False).alert("k", "x")   # must not raise


class WiringTest(unittest.TestCase):
    def test_a_failed_model_call_alerts_with_the_error_type_only(self):
        from app import llm
        with patch.object(llm.settings, "ai_provider", "gemini"), \
                patch.object(llm, "get_gemini_llm", side_effect=ValueError("key=SECRET-KEY-VALUE student text")), \
                patch.object(alerts, "alert") as alert:
            with self.assertRaises(llm.AiUnavailable):
                llm.invoke_json("system", "user")
        kind, text = alert.call_args[0]
        self.assertEqual("gemini:ValueError", kind)
        self.assertNotIn("SECRET-KEY-VALUE", text)
        self.assertNotIn("student text", text)


if __name__ == "__main__":
    unittest.main()
