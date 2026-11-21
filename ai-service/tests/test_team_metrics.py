import unittest

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app.analysis.team_metrics import analyse_team, default_actions, plain_summary


def member(i, name=None):
    return {"studentId": i, "studentName": name or f"Student {i}"}


def rating(evaluator, evaluated, pct):
    return {"evaluatorId": evaluator, "evaluatedId": evaluated, "earned": pct, "possible": 100.0}


def team_with(peer_pcts):
    """peer_pcts: {evaluated_id: [pct from each other member]} for a 4-person team."""
    ratings = []
    for evaluated, values in peer_pcts.items():
        raters = [m for m in (1, 2, 3, 4) if m != evaluated]
        for rater, pct in zip(raters, values):
            ratings.append(rating(rater, evaluated, pct))
    return [member(i) for i in (1, 2, 3, 4)], ratings


class FlagsAreCalculatedWithEvidenceTest(unittest.TestCase):
    def test_a_free_rider_is_flagged_with_the_numbers_behind_it(self):
        members, ratings = team_with({1: [85, 82, 88], 2: [80, 84, 86], 3: [83, 85, 81], 4: [50, 55, 48]})

        result = analyse_team(members, ratings)

        flags = [f for f in result["flags"] if f["type"] == "FREE_RIDER"]
        self.assertEqual(len(flags), 1)
        self.assertEqual(flags[0]["targetStudent"], "Student 4")
        self.assertEqual(flags[0]["source"], "CALCULATED")
        self.assertIn("3 teammates", flags[0]["evidence"])
        self.assertEqual(result["member_stats"][4]["status"], "SUSPECTED_FREE_RIDER")
        self.assertEqual(result["status"], "FREE_RIDER_DETECTED")
        self.assertLess(result["health_score"], 80)

    def test_a_student_nobody_has_rated_is_not_called_a_free_rider(self):
        members, ratings = team_with({1: [85, 82, 88], 2: [80, 84, 86], 3: [83, 85, 81]})   # student 4 has no peer ratings

        result = analyse_team(members, ratings)

        self.assertEqual(result["member_stats"][4]["status"], "UNDER_EVALUATED")
        self.assertIsNone(result["member_stats"][4]["peerScoreAvg"])
        self.assertFalse([f for f in result["flags"] if f["type"] == "FREE_RIDER"])

    def test_self_inflation_compares_self_rating_with_peer_average(self):
        members, ratings = team_with({1: [70, 72, 68], 2: [80, 82, 78], 3: [80, 82, 78], 4: [80, 82, 78]})
        ratings.append(rating(1, 1, 95))

        result = analyse_team(members, ratings)

        flags = [f for f in result["flags"] if f["type"] == "SELF_INFLATION"]
        self.assertEqual([f["targetStudent"] for f in flags], ["Student 1"])
        self.assertIn("95", flags[0]["evidence"])
        self.assertEqual(result["member_stats"][1]["status"], "OVER_CONFIDENT")

    def test_identical_top_marks_all_round_are_raised_as_a_possible_agreement_not_a_verdict(self):
        members, ratings = team_with({1: [96, 97, 96], 2: [97, 96, 97], 3: [96, 96, 97], 4: [97, 97, 96]})

        result = analyse_team(members, ratings)

        flags = [f for f in result["flags"] if f["type"] == "COLLUSION"]
        self.assertEqual(len(flags), 1)
        self.assertIn("check against what you observed", flags[0]["evidence"])
        self.assertEqual(result["status"], "SUSPECTED_COLLUSION")

    def test_ordinary_varied_ratings_raise_nothing(self):
        members, ratings = team_with({1: [85, 82, 88], 2: [80, 84, 86], 3: [83, 85, 81], 4: [79, 83, 84]})

        result = analyse_team(members, ratings)

        self.assertEqual(result["flags"], [])
        self.assertEqual(result["health_score"], 100)
        self.assertEqual(result["status"], "HEALTHY_COLLABORATION")


class SmallAndOddDataTest(unittest.TestCase):
    def test_with_almost_no_data_there_is_no_score_and_no_judgement(self):
        result = analyse_team([member(1), member(2)], [rating(2, 1, 40)])

        self.assertIsNone(result["health_score"])
        self.assertEqual(result["status"], "INSUFFICIENT_DATA")
        self.assertEqual(result["flags"], [])

    def test_ratings_with_no_marks_available_are_ignored_not_counted_as_zero(self):
        members, ratings = team_with({1: [85, 82, 88], 2: [80, 84, 86], 3: [83, 85, 81], 4: [79, 83, 84]})
        ratings.append({"evaluatorId": 2, "evaluatedId": 1, "earned": 0.0, "possible": 0.0})

        result = analyse_team(members, ratings)

        self.assertEqual(result["member_stats"][1]["peerRatingCount"], 3)

    def test_the_summary_never_claims_more_than_the_numbers_show(self):
        quiet = analyse_team(*team_with({1: [85, 82, 88], 2: [80, 84, 86], 3: [83, 85, 81], 4: [79, 83, 84]}))
        thin = analyse_team([member(1), member(2)], [])

        self.assertIn("no calculated warning signs", plain_summary("Team A", quiet))
        self.assertIn("Read the written comments", plain_summary("Team A", quiet))
        self.assertIn("not enough peer ratings", plain_summary("Team A", thin))
        self.assertEqual(default_actions(thin), ["Wait until more peer evaluations are submitted before drawing conclusions."])


if __name__ == "__main__":
    unittest.main()
