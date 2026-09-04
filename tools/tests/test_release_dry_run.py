import unittest
import tempfile
import json
from pathlib import Path
from tools.release_dry_run import (
    check_reactor_pom_versions,
    check_tck_conformance_gate,
    check_changelog_section,
    run_preflight_checks,
)


class TestReleaseDryRun(unittest.TestCase):
    def test_check_changelog_section(self):
        errors = check_changelog_section("1.0.0")
        self.assertEqual(len(errors), 0)

    def test_check_changelog_section_missing(self):
        errors = check_changelog_section("99.99.99")
        self.assertEqual(len(errors), 1)
        self.assertIn("CHANGELOG.md has no release section", errors[0])

    def test_check_tck_conformance_gate(self):
        errors = check_tck_conformance_gate()
        self.assertEqual(len(errors), 0)

    def test_run_preflight_checks_simulation(self):
        res = run_preflight_checks(simulated_version="1.0.0")
        self.assertTrue(res["passed"])
        self.assertEqual(len(res["all_errors"]), 0)


if __name__ == "__main__":
    unittest.main()
