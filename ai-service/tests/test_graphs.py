import unittest

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app.graphs.moderation_graph import compute_statistical_gaps


class TestGraphs(unittest.TestCase):

    def test_compute_statistical_gaps_moderation(self):
        scores = [
            {
                "lecturerId": 1, "lecturerName": "Dr. Alice",
                "targetId": "team_1", "targetName": "Team Alpha",
                "criterionId": "crit_1", "earned": 80.0
            },
            {
                "lecturerId": 2, "lecturerName": "Dr. Bob",
                "targetId": "team_1", "targetName": "Team Alpha",
                "criterionId": "crit_1", "earned": 50.0
            }
        ]
        criteria = [
            {
                "id": "crit_1", "label": "Architecture",
                "rubricName": "Technical Rubric", "possible": 100.0
            }
        ]
        req = {
            "assessmentId": 10,
            "assessmentTitle": "Capstone",
            "criteria": criteria,
            "scores": scores,
            "comments": []
        }
        state = {
            "request": req,
            "cohort_gap_pct": 0.0,
            "consensus_status": "",
            "top_divergent_criteria": [],
            "priority_targets": [],
            "executive_summary": "",
            "moderation_actions": [],
            "ai_attempts": 0,
            "ai_failures": 0,
            "ai_notes": [],
            "final_response": None
        }

        stats = compute_statistical_gaps(state)
        self.assertEqual(stats["cohort_gap_pct"], 30.0)
        self.assertEqual(stats["consensus_status"], "ACTION_REQUIRED")
        self.assertEqual(len(stats["top_divergent_criteria"]), 1)
        self.assertEqual(stats["top_divergent_criteria"][0]["criterionId"], "crit_1")
        self.assertEqual(stats["top_divergent_criteria"][0]["meanPairGapPct"], 30.0)
        self.assertEqual(len(stats["priority_targets"]), 1)
        self.assertEqual(stats["priority_targets"][0]["spreadPct"], 30.0)


if __name__ == "__main__":
    unittest.main()
