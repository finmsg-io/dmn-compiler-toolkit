from pathlib import Path
import copy
import json
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_documentation  # noqa: E402


class VerifyDocumentationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.data = json.loads((verify_documentation.ROOT / "docs/assets/tck-accounting-sparksql-native.json")
                               .read_text(encoding="utf-8"))
        self.page = (verify_documentation.ROOT / "docs/tck-conformance.md").read_text(encoding="utf-8")

    def assert_evidence_error(self, data: dict, page: str, reason: str) -> None:
        errors = verify_documentation.validate_spark_evidence(data, page)
        self.assertTrue(errors)
        self.assertIn(reason, errors[0])

    def test_current_evidence_and_historical_metrics_are_separate(self) -> None:
        page = self.page + "\nHistorical checkpoint: 2,715 native, 676 rejected.\n"
        self.assertEqual([], verify_documentation.validate_spark_evidence(self.data, page))

    def test_duplicate_case_keys_fail_even_with_unchanged_totals(self) -> None:
        self.data["outcomes"][1] = copy.deepcopy(self.data["outcomes"][0])
        self.assert_evidence_error(self.data, self.page, "duplicate case key")

    def test_wrong_backend_and_revision_fail(self) -> None:
        data = copy.deepcopy(self.data)
        data["backends"] = ["optimized-interpreter"]
        self.assert_evidence_error(data, self.page, "backend")
        self.data["revision"] = "0" * 40
        self.assert_evidence_error(self.data, self.page, "revision mismatch")

    def test_outcome_backend_must_match_metadata(self) -> None:
        self.data["outcomes"][0]["backend"] = "optimized-generated-java"
        self.assert_evidence_error(self.data, self.page, "outcome backend")

    def test_missing_case_and_inconsistent_totals_fail(self) -> None:
        data = copy.deepcopy(self.data)
        data["outcomes"].pop()
        self.assert_evidence_error(data, self.page, "all 3,391")
        self.data["statusCounts"]["PASSED"] -= 1
        self.assert_evidence_error(self.data, self.page, "status totals")

    def test_fallback_and_static_rejection_cannot_support_all_native_claim(self) -> None:
        for diagnostic in ("FALLBACK", "EXPECTED_MODEL_REJECTION"):
            self.data["outcomes"][0]["diagnostic"] = diagnostic
            self.assert_evidence_error(self.data, self.page, "NATIVE routing")

    def test_wrong_count_or_percentage_in_current_table_fails(self) -> None:
        for old, new in (("**3,391**", "**3,390**"), ("**100.00%**", "**99.97%**")):
            self.assert_evidence_error(self.data, self.page.replace(old, new, 1), "table does not match")

    def test_missing_or_malformed_evidence_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory, patch.object(verify_documentation, "ROOT", Path(directory)):
            self.assertIn("unavailable or malformed", verify_documentation.spark_evidence_errors()[0])
            evidence = Path(directory) / "docs/assets/tck-accounting-sparksql-native.json"
            evidence.parent.mkdir(parents=True)
            evidence.write_text("{broken", encoding="utf-8")
            self.assertIn("unavailable or malformed", verify_documentation.spark_evidence_errors()[0])

    def test_documented_modules_match_reactor(self) -> None:
        reactor = verify_documentation.reactor_modules()
        documented = verify_documentation.documented_modules()
        self.assertEqual(reactor, documented)

    def test_verification_succeeds(self) -> None:
        self.assertEqual(0, verify_documentation.main())


if __name__ == "__main__":
    unittest.main()
