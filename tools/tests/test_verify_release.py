from pathlib import Path
import sys
import unittest
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_release  # noqa: E402


class VerifyReleaseTest(unittest.TestCase):
    def test_accepts_strict_release_and_rc_tags(self) -> None:
        self.assertIsNotNone(verify_release.SEMVER_TAG.fullmatch("v1.2.3"))
        self.assertIsNotNone(verify_release.SEMVER_TAG.fullmatch("v1.2.3-rc.1"))

    def test_rejects_loose_or_snapshot_tags(self) -> None:
        for tag in ("1.2.3", "v1.2", "v01.2.3", "v1.2.3-SNAPSHOT", "v1.2.3-rc.0"):
            with self.subTest(tag=tag):
                self.assertIsNone(verify_release.SEMVER_TAG.fullmatch(tag))

    @patch.object(verify_release, "root_model", return_value=(None, "1.0.0-SNAPSHOT", []))
    def test_snapshot_reactor_cannot_be_released(self, _root_model) -> None:
        errors = verify_release.validate("v1.0.0")
        self.assertIn("tag version 1.0.0 does not match root Maven version 1.0.0-SNAPSHOT", errors)
        self.assertIn("release Maven version must not be a snapshot: 1.0.0-SNAPSHOT", errors)

    def test_release_validation_on_current_reactor(self) -> None:
        errors = verify_release.validate("v1.0.0-rc.2", require_artifacts=False)
        self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
