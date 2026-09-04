from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_checksums  # noqa: E402


class GenerateChecksumsTest(unittest.TestCase):
    def test_compute_hash(self) -> None:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as f:
            f.write("hello world")
            f.flush()
            temp_path = Path(f.name)

        try:
            sha256 = generate_checksums.compute_hash(temp_path, "sha256")
            self.assertEqual("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", sha256)
        finally:
            temp_path.unlink(missing_ok=True)

    def test_generate_and_verify_checksums(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir_str:
            base_dir = Path(temp_dir_str)
            target_dir = base_dir / "dmn-compiler" / "target"
            target_dir.mkdir(parents=True, exist_ok=True)

            file1 = target_dir / "dmn-compiler-1.0.0.jar"
            file1.write_bytes(b"mock jar binary")
            file2 = base_dir / "pom.xml"
            file2.write_text("<project></project>", encoding="utf-8")

            artifacts = generate_checksums.find_release_artifacts(base_dir)
            self.assertEqual(2, len(artifacts))

            checksum_text = generate_checksums.generate_checksum_file(artifacts, base_dir, "sha256")
            checksum_file = base_dir / "checksums.sha256"
            checksum_file.write_text(checksum_text, encoding="utf-8")

            errors = generate_checksums.verify_checksum_file(checksum_file, base_dir, "sha256")
            self.assertEqual([], errors)

    def test_verify_detects_tampered_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir_str:
            base_dir = Path(temp_dir_str)
            target_dir = base_dir / "target"
            target_dir.mkdir(parents=True, exist_ok=True)

            file1 = target_dir / "test.jar"
            file1.write_bytes(b"original")

            checksum_text = generate_checksums.generate_checksum_file([file1], base_dir, "sha256")
            checksum_file = base_dir / "checksums.sha256"
            checksum_file.write_text(checksum_text, encoding="utf-8")

            # Tamper with file
            file1.write_bytes(b"tampered")

            errors = generate_checksums.verify_checksum_file(checksum_file, base_dir, "sha256")
            self.assertEqual(1, len(errors))
            self.assertIn("hash mismatch", errors[0])


if __name__ == "__main__":
    unittest.main()
