import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

import _quiet  # noqa: F401  (no real Telegram messages from tests)
from app import main
from app.schemas.team_dynamics_schemas import TeamDynamicsResponse

SECRET = "a-long-random-shared-secret-123"
BODY = {"groupId": 1, "groupName": "T", "assessmentId": 1, "assessmentTitle": "A",
        "members": [{"studentId": 1, "studentName": "A"}]}


def ok_response():
    return TeamDynamicsResponse(groupId=1, groupName="T", collaborationStatus="INSUFFICIENT_DATA", executiveSummary="s")


class StartupSecurityTest(unittest.TestCase):
    def test_refuses_to_start_without_a_real_secret(self):
        for weak in ("", "changeme", "dev-insecure-secret-change-in-env", "dev-internal-secret-token-change-in-env", "short", "CHANGE_ME_generate_with_openssl_rand_hex_24", "dev-anything-long-enough-here"):
            with self.assertRaises(RuntimeError, msg=weak):
                main.validate_security(weak, debug=False)

    def test_starts_with_a_long_random_secret_and_debug_is_the_only_exception(self):
        main.validate_security(SECRET, debug=False)
        main.validate_security("", debug=True)


class RequestAuthenticationTest(unittest.TestCase):
    def setUp(self):
        self.patches = [patch.object(main.settings, "ai_internal_secret", SECRET), patch.object(main.settings, "debug", False)]
        for p in self.patches:
            p.start()
        self.client = TestClient(main.app)

    def tearDown(self):
        for p in self.patches:
            p.stop()

    def test_a_missing_or_wrong_secret_is_refused(self):
        self.assertEqual(self.client.post("/api/v1/team-dynamics/audit", json=BODY).status_code, 401)
        self.assertEqual(self.client.post("/api/v1/team-dynamics/audit", json=BODY, headers={"X-Internal-Secret": "nope"}).status_code, 401)

    def test_the_right_secret_is_accepted(self):
        with patch.object(main, "execute_team_dynamics_audit", return_value=ok_response()):
            r = self.client.post("/api/v1/team-dynamics/audit", json=BODY, headers={"X-Internal-Secret": SECRET})
        self.assertEqual(r.status_code, 200)

    def test_an_empty_configured_secret_never_lets_anyone_in_outside_debug(self):
        with patch.object(main.settings, "ai_internal_secret", ""):
            r = self.client.post("/api/v1/team-dynamics/audit", json=BODY, headers={"X-Internal-Secret": ""})
        self.assertEqual(r.status_code, 401)

    def test_health_says_healthy_and_nothing_about_keys_or_models(self):
        r = self.client.get("/health")
        self.assertEqual(r.json(), {"status": "healthy", "service": "IAMS AI Service"})

    def test_browsers_get_no_cors_permission(self):
        r = self.client.get("/health", headers={"Origin": "https://evil.example"})
        self.assertNotIn("access-control-allow-origin", {k.lower() for k in r.headers})

    def test_an_internal_failure_does_not_leak_its_message(self):
        with patch.object(main, "execute_team_dynamics_audit", side_effect=Exception("db password is hunter2 at http://internal")):
            r = self.client.post("/api/v1/team-dynamics/audit", json=BODY, headers={"X-Internal-Secret": SECRET})
        self.assertEqual(r.status_code, 500)
        self.assertNotIn("hunter2", r.text)
        self.assertNotIn("internal", r.text)

    def test_an_oversized_request_is_rejected(self):
        big = dict(BODY, peerRatings=[{"evaluatorId": 1, "evaluatorName": "a", "evaluatedId": 2, "evaluatedName": "b",
                                       "earned": 1, "possible": 2}] * 6001)
        r = self.client.post("/api/v1/team-dynamics/audit", json=big, headers={"X-Internal-Secret": SECRET})
        self.assertEqual(r.status_code, 422)


if __name__ == "__main__":
    unittest.main()
