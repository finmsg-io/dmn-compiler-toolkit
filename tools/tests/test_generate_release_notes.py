from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_release_notes  # noqa: E402


class GenerateReleaseNotesTest(unittest.TestCase):
    SAMPLE_CHANGELOG = """# Changelog

## [Unreleased]

### Added
- Unreleased feature A

## [1.2.0] — 2026-08-20

### Added
- Feature 1.2

### Fixed
- Bug fix 1.2

## [1.1.0]

### Added
- Feature 1.1
"""

    def test_extract_unreleased(self) -> None:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as f:
            f.write(self.SAMPLE_CHANGELOG)
            f.flush()
            temp_path = Path(f.name)

        try:
            notes = generate_release_notes.extract_notes(temp_path, "unreleased")
            self.assertIn("Unreleased feature A", notes)
            self.assertNotIn("Feature 1.2", notes)
        finally:
            temp_path.unlink(missing_ok=True)

    def test_extract_specific_version(self) -> None:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as f:
            f.write(self.SAMPLE_CHANGELOG)
            f.flush()
            temp_path = Path(f.name)

        try:
            notes = generate_release_notes.extract_notes(temp_path, "v1.2.0")
            self.assertIn("Feature 1.2", notes)
            self.assertIn("Bug fix 1.2", notes)
            self.assertNotIn("Feature 1.1", notes)
            self.assertNotIn("Unreleased feature A", notes)
        finally:
            temp_path.unlink(missing_ok=True)

    def test_missing_version_raises(self) -> None:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as f:
            f.write(self.SAMPLE_CHANGELOG)
            f.flush()
            temp_path = Path(f.name)

        try:
            with self.assertRaises(ValueError):
                generate_release_notes.extract_notes(temp_path, "v9.9.9")
        finally:
            temp_path.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
