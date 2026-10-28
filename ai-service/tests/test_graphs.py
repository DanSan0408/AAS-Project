import sys
import unittest
from unittest.mock import MagicMock

# Stub external dependencies if not yet installed in host environment
for mod in ["langchain_core", "langchain_core.messages", "langgraph", "langgraph.graph", "langchain_google_genai", "pydantic_settings", "pydantic"]:
    if mod not in sys.modules:
        mock_mod = MagicMock()
        sys.modules[mod] = mock_mod

from app.graphs.moderation_graph import compute_statistical_gaps
from app.graphs.team_dynamics_graph import compute_peer_metrics


class TestGraphs(unittest.TestCase):

    def test_compute_peer_metrics_team_dynamics(self):
        members = [
            {"studentId": 1, "studentName": "Alice", "email": "alice@domain.com"},
            {"studentId": 2, "studentName": "Bob", "email": "bob@domain.com"}
        ]
        # Alice rates self 90/100, rates Bob 60/100
        # Bob rates self 80/100, rates Alice 85/100
        peer_ratings = [
            {"evaluatorId": 1, "evaluatedId": 1, "earned": 90.0, "possible": 100.0},
            {"evaluatorId": 1, "evaluatedId": 2, "earned": 60.0, "possible": 100.0},
            {"evaluatorId": 2, "evaluatedId": 2, "earned": 80.0, "possible": 100.0},
            {"evaluatorId": 2, "evaluatedId": 1, "earned": 85.0, "possible": 100.0},
        ]
        state = {
            "request": {
                "groupId": 10,
                "groupName": "Team Alpha",
                "assessmentId": 100,
                "assessmentTitle": "Capstone Sprint 1",
                "members": members,
                "peerRatings": peer_ratings,
                "peerComments": []
            },
            "member_stats": {},
            "group_peer_avg": 0.0,
            "sentiment_notes": [],
            "anomaly_flags": [],
            "team_health_score": 100,
            "collaboration_status": "",
            "executive_summary": "",
            "recommended_actions": [],
            "final_response": None
        }

        result = compute_peer_metrics(state)
        stats = result["member_stats"]

        # Alice: self 90%, peer 85%, inflation = 90 - 85 = 5.0
        self.assertEqual(stats[1]["selfScoreAvg"], 90.0)
        self.assertEqual(stats[1]["peerScoreAvg"], 85.0)
        self.assertEqual(stats[1]["selfInflationGap"], 5.0)

        # Bob: self 80%, peer 60%, inflation = 80 - 60 = 20.0
        self.assertEqual(stats[2]["selfScoreAvg"], 80.0)
        self.assertEqual(stats[2]["peerScoreAvg"], 60.0)
        self.assertEqual(stats[2]["selfInflationGap"], 20.0)

        # Group peer average = (85 + 60) / 2 = 72.5
        self.assertEqual(result["group_peer_avg"], 72.5)

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
