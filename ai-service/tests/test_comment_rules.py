import json
import os
import unittest

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app.analysis.comment_rules import check

DATA = os.path.join(os.path.dirname(__file__), "data", "comment_eval.jsonl")


def load():
    with open(DATA, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


class ObfuscationTest(unittest.TestCase):
    def test_disguised_profanity_is_still_found(self):
        for text in ("f u c k i n g hell", "what a sh1t team", "fuuuuck this", "dia b0doh", "f*ck you", "B O D O H punya", "S.I.A.L"):
            verdict = check(text)
            self.assertIsNotNone(verdict, text)
            self.assertEqual(verdict.label, "abusive", text)

    def test_ordinary_words_that_merely_contain_a_bad_word_are_left_alone(self):
        for text in ("Scunthorpe is a town", "She is a classic example", "Please assist with the passwords", "The babysitter helped",
                     "anjingan is not a word we use here but analyse the data"):
            self.assertIsNone(check(text), text)


class WhatTheWordListNeverDoesTest(unittest.TestCase):
    def test_it_never_calls_a_comment_fine(self):
        for row in load():
            verdict = check(row["text"])
            self.assertTrue(verdict is None or verdict.label != "ok", row["text"])

    def test_blunt_but_fair_criticism_is_not_escalated_to_abusive_or_injection(self):
        for row in load():
            if row["label"] in ("ok", "negative_constructive"):
                verdict = check(row["text"])
                if verdict is not None:
                    self.assertIn(verdict.kind, ("mild", "strong_quoted"), f"{row['text']} -> {verdict}")

    def test_quoted_profanity_in_a_report_of_what_was_said_asks_for_a_look_but_is_not_called_abusive(self):
        for text in ("He said 'f*** this project' in the meeting and left.", "Kata dia 'babi la projek ni' lepas tu keluar bilik."):
            verdict = check(text)
            self.assertEqual(verdict.kind, "strong_quoted", text)
            self.assertEqual(verdict.label, "unclear")


class WhatItCatchesTest(unittest.TestCase):
    def test_every_injection_attempt_in_the_set_is_caught(self):
        attempts = [r for r in load() if r["label"] == "injection_attempt"]
        self.assertGreaterEqual(len(attempts), 5)
        for row in attempts:
            verdict = check(row["text"])
            self.assertIsNotNone(verdict, row["text"])
            self.assertEqual(verdict.label, "injection_attempt", row["text"])

    def test_most_abusive_comments_get_at_least_a_flag_from_the_word_list_alone(self):
        abusive = [r for r in load() if r["label"] == "abusive"]
        flagged = [r for r in abusive if check(r["text"]) is not None]
        self.assertGreaterEqual(len(flagged) / len(abusive), 0.8, f"word list missed: {[r['text'] for r in abusive if r not in flagged]}")

    def test_discriminatory_generalisations_are_flagged(self):
        for text in ("All Malays are lazy, that is why we failed.", "Semua cina sama, penipu belaka.", "semua india malas"):
            verdict = check(text)
            self.assertEqual(verdict.kind, "discrimination", text)

    def test_the_set_is_large_and_covers_all_three_languages(self):
        rows = load()
        self.assertGreaterEqual(len(rows), 80)
        self.assertEqual({r["language"] for r in rows if r["language"] != "other"}, {"en", "ms", "mixed"})


if __name__ == "__main__":
    unittest.main()
