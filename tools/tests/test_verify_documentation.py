from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_documentation  # noqa: E402


class VerifyDocumentationTest(unittest.TestCase):
    def test_documented_modules_match_reactor(self) -> None:
        reactor = verify_documentation.reactor_modules()
        documented = verify_documentation.documented_modules()
        self.assertEqual(reactor, documented)

    def test_verification_succeeds(self) -> None:
        self.assertEqual(0, verify_documentation.main())


if __name__ == "__main__":
    unittest.main()
