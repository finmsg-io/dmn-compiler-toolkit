from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ci_impact


class CiImpactTest(unittest.TestCase):
    def test_lightweight_changes(self):
        self.assertFalse(ci_impact.needs_heavy(["README.md", "docs/guide.md", "prompts/actual-task.md", "tools/central_status.py", ".github/workflows/ci.yml", "requirements-docs.txt", "mkdocs.yml"]))

    def test_product_and_build_changes_require_full_verification(self):
        for path in ["pom.xml", "dmn-runtime/pom.xml", "dmn-runtime/src/main/java/Eval.java", "dmn-runtime/src/test/java/EvalTest.java", "dmn-protobuf/src/main/proto/model.proto", ".mvn/maven.config", "unknown/new-file"]:
            with self.subTest(path=path): self.assertTrue(ci_impact.needs_heavy([path]))

    def test_mixed_changes_are_heavy(self):
        self.assertTrue(ci_impact.needs_heavy(["docs/guide.md", "dmn-runtime/src/main/java/Eval.java"]))

    def test_missing_or_invalid_base_is_conservative(self):
        for base in [None, "", "0" * 40, "--output=untrusted"]:
            with self.subTest(base=base): self.assertTrue(ci_impact.inspect(base))

    def test_diff_failure_cannot_skip_full_verification(self):
        with patch("ci_impact.subprocess.check_output", side_effect=subprocess.CalledProcessError(1, "git")):
            self.assertTrue(ci_impact.inspect("a" * 40))

    def test_null_delimited_paths_with_spaces(self):
        with patch("ci_impact.subprocess.check_output", return_value=b"docs/guide with spaces.md\0tools/ci_impact.py\0"):
            self.assertFalse(ci_impact.inspect("a" * 40))

    def test_workflow_builds_once_and_keeps_all_backends(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/ci.yml").read_text()
        self.assertEqual(1, workflow.count("run: mvn "))
        self.assertIn("-Dtck.backend=all", workflow)
        self.assertIn("-Dtck.requireFull=true", workflow)
        self.assertNotIn('      - "**"', workflow)
        self.assertIn("cancel-in-progress: true", workflow)
        self.assertIn("Upload optimized TCK accounting", workflow)
        release = (root / ".github/workflows/publish-release.yml").read_text()
        self.assertEqual(1, release.count(" clean verify"))
        self.assertNotIn("run: mvn --batch-mode -Ptck verify", release)

    def test_move_into_docs_cannot_hide_product_deletion(self):
        with patch("ci_impact.subprocess.check_output", return_value=b"dmn-runtime/src/main/java/Eval.java\0docs/Eval.java\0") as diff:
            self.assertTrue(ci_impact.inspect("a" * 40))
        self.assertIn("--no-renames", diff.call_args.args[0])
