import unittest

from tools.eval_comments import score
from tools.export_comments import redact


class RedactionTest(unittest.TestCase):
    def test_personal_details_are_masked_before_anyone_labels_a_comment(self):
        text = "Mail ali@uni.edu.my or call 012-345 6789, IC 900101-14-5678, see https://x.example/a?b=1"
        masked = redact(text)
        for private in ("ali@uni.edu.my", "012-345 6789", "900101-14-5678", "https://x.example"):
            self.assertNotIn(private, masked)
        self.assertIn("[email]", masked)
        self.assertIn("[phone]", masked)


class ScoringTest(unittest.TestCase):
    ROWS = [{"id": "1", "text": "fair a", "label": "ok"}, {"id": "2", "text": "fair b", "label": "negative_constructive"},
            {"id": "3", "text": "bad a", "label": "abusive"}, {"id": "4", "text": "bad b", "label": "sarcastic"}]

    def test_the_false_flag_rate_counts_fair_comments_called_a_problem(self):
        predicted = {"1": {"label": "abusive", "flagged": True}, "2": {"label": "negative_constructive", "flagged": False},
                     "3": {"label": "abusive", "flagged": True}, "4": {"label": "ok", "flagged": False}}

        result = score(self.ROWS, predicted)

        self.assertEqual(result["false_flag_rate"], 0.5)
        self.assertEqual(result["wrongly_flagged"], ["fair a"])
        self.assertEqual(result["problem_missed_rate"], 0.5)
        self.assertEqual(result["missed"], ["bad b"])
        self.assertEqual(result["per_label"]["abusive"]["precision"], 0.5)
        self.assertEqual(result["per_label"]["abusive"]["recall"], 1.0)

    def test_fair_comments_sent_to_review_are_counted_separately_from_wrongly_called_problems(self):
        predicted = {"1": {"label": "ok", "flagged": True}, "2": {"label": "negative_constructive", "flagged": False},
                     "3": {"label": "abusive", "flagged": True}, "4": {"label": "sarcastic", "flagged": True}}

        result = score(self.ROWS, predicted)

        self.assertEqual(result["false_flag_rate"], 0.0)
        self.assertEqual(result["fair_sent_to_review_rate"], 0.5)


if __name__ == "__main__":
    unittest.main()
