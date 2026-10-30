import base64
import contextlib
import io
import json
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import central_status


class CentralStatusTest(unittest.TestCase):
    def environment(self):
        return {"CENTRAL_DEPLOYMENT_ID": "492200a5-b675-4b7a-b1df-42ce8bf1485f",
                "SONATYPE_USERNAME": "synthetic-user-token", "SONATYPE_PASSWORD": "synthetic-password-token"}

    def test_published_status_uses_only_status_endpoint(self):
        response = io.BytesIO(json.dumps({"deploymentState": "PUBLISHED"}).encode())
        output = io.StringIO()
        with patch.dict(os.environ, self.environment(), clear=True), patch(
                "central_status.urllib.request.urlopen", return_value=response) as request, contextlib.redirect_stdout(output):
            self.assertEqual(0, central_status.main())
        call = request.call_args.args[0]
        self.assertEqual("POST", call.method)
        self.assertEqual("https://central.sonatype.com/api/v1/publisher/status?id=492200a5-b675-4b7a-b1df-42ce8bf1485f", call.full_url)
        self.assertEqual("PUBLISHED", json.loads(output.getvalue())["deploymentState"])
        self.assertNotIn("synthetic-user-token", output.getvalue())

    def test_failed_validation_returns_failure_and_redacts_credentials(self):
        env = self.environment()
        token = base64.b64encode((env["SONATYPE_USERNAME"] + ":" + env["SONATYPE_PASSWORD"]).encode()).decode()
        response = io.BytesIO(json.dumps({"deploymentState": "FAILED", "errors": [env["SONATYPE_USERNAME"], env["SONATYPE_PASSWORD"], token]}).encode())
        output = io.StringIO()
        with patch.dict(os.environ, env, clear=True), patch("central_status.urllib.request.urlopen", return_value=response), contextlib.redirect_stdout(output):
            self.assertEqual(1, central_status.main())
        for secret in [env["SONATYPE_USERNAME"], env["SONATYPE_PASSWORD"], token]:
            self.assertNotIn(secret, output.getvalue())
        self.assertEqual("FAILED", json.loads(output.getvalue())["deploymentState"])

    def test_invalid_identifier_cannot_change_endpoint(self):
        with patch.dict(os.environ, {"CENTRAL_DEPLOYMENT_ID": "not-a-uuid"}, clear=True), patch(
                "central_status.urllib.request.urlopen") as request, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, central_status.main())
        request.assert_not_called()

    def test_missing_credentials_never_calls_endpoint(self):
        with patch.dict(os.environ, {"CENTRAL_DEPLOYMENT_ID": self.environment()["CENTRAL_DEPLOYMENT_ID"]}, clear=True), patch(
                "central_status.urllib.request.urlopen") as request, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, central_status.main())
        request.assert_not_called()
