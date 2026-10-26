from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from report_coverage import collect, coverage_counter  # noqa: E402


class ReportCoverageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.module = self.root / "module-a"
        self.module.mkdir()
        (self.root / "pom.xml").write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>module-a</module>'
            '</modules></project>', encoding="utf-8")

    def fixture(self, failures=0):
        source = self.module / "src/main/java/Example.java"
        source.parent.mkdir(parents=True)
        source.write_text("class Example {}", encoding="utf-8")
        report = self.module / "target/site/jacoco/jacoco.xml"
        report.parent.mkdir(parents=True)
        report.write_text('<report><package name="example"><class name="example/Example"/></package>'
                          '<counter type="LINE" covered="5" missed="5"/>'
                          '<counter type="BRANCH" covered="1" missed="3"/></report>', encoding="utf-8")
        tests = self.module / "target/surefire-reports/TEST-example.xml"
        tests.parent.mkdir(parents=True)
        tests.write_text(f'<testsuite tests="2" failures="{failures}" errors="0" skipped="0"/>', encoding="utf-8")
        return report

    def test_measured_line_and_branch_counters(self):
        self.fixture()
        module = collect(self.root)["modules"][0]
        self.assertEqual({"covered": 5, "missed": 5, "total": 10, "percent": 50.0}, module["line"])
        self.assertEqual(25.0, module["branch"]["percent"])
        self.assertEqual(2, module["testOutcomes"]["tests"])

    def test_generated_or_test_only_module_is_not_zero_coverage(self):
        module = collect(self.root)["modules"][0]
        self.assertEqual("N/A", module["status"])
        self.assertNotIn("line", module)

    def test_missing_report_and_failed_tests_do_not_establish_baseline(self):
        report = self.fixture(failures=1)
        with self.assertRaisesRegex(ValueError, "failing tests"):
            collect(self.root)
        report.unlink()
        with self.assertRaisesRegex(ValueError, "missing JaCoCo"):
            collect(self.root)

    def test_zero_branch_denominator_is_not_zero_percent(self):
        counter = coverage_counter(ET.fromstring('<report/>'), "BRANCH")
        self.assertEqual(0, counter["total"])
        self.assertIsNone(counter["percent"])

    def test_negative_or_duplicate_counters_fail(self):
        for xml in ('<report><counter type="LINE" covered="-1" missed="2"/></report>',
                    '<report><counter type="LINE" covered="1" missed="2"/>'
                    '<counter type="LINE" covered="1" missed="2"/></report>'):
            with self.assertRaises(ValueError):
                coverage_counter(ET.fromstring(xml), "LINE")

    def test_accidentally_excluding_all_classes_fails(self):
        report = self.fixture()
        report.write_text('<report><counter type="LINE" covered="0" missed="0"/></report>', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "no handwritten classes"):
            collect(self.root)

    def test_parser_report_keeps_facade_and_excludes_generated_parser(self):
        self.module = self.root / "dmn-feel-parser"
        self.module.mkdir()
        self.fixture()
        pom = self.root / "pom.xml"
        pom.write_text(pom.read_text().replace("module-a", "dmn-feel-parser"), encoding="utf-8")
        report = self.module / "target/site/jacoco/jacoco.xml"
        text = report.read_text().replace("example/Example", "io/finmsg/dmn/feel/parser/FeelParserFacade")
        report.write_text(text, encoding="utf-8")
        self.assertEqual("measured", collect(self.root)["modules"][0]["status"])
        report.write_text(text.replace("FeelParserFacade", "FeelParser$Context"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "generated ANTLR"):
            collect(self.root)
        report.write_text(text.replace("FeelParserFacade", "FeelAstBuilder"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Facade was accidentally excluded"):
            collect(self.root)


if __name__ == "__main__":
    unittest.main()
