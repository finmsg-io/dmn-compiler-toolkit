from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from documentation_links import check_links, navigation_paths, documentation_link_errors  # noqa: E402


class DocumentationLinksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.page = self.root / "README.md"

    def check(self, text):
        self.page.write_text(text, encoding="utf-8")
        return check_links(self.root, [self.page], ["toc", "fenced_code", "attr_list"], {})

    def test_inline_reference_image_encoded_path_and_fragment(self):
        (self.root / "Other Page.md").write_text("# Some heading\n<a id='explicit'></a>", encoding="utf-8")
        (self.root / "image.png").write_bytes(b"synthetic image target")
        self.assertEqual([], self.check(
            "[inline](Other%20Page.md#some-heading)\n[reference][r]\n"
            "[r]: Other%20Page.md#explicit\n\n![image](image.png)\n"))

    def test_missing_file_and_missing_fragment(self):
        (self.root / "other.md").write_text("# Exists", encoding="utf-8")
        errors = self.check("[missing](no.md)\n[bad anchor](other.md#absent)")
        self.assertEqual(2, len(errors))
        self.assertIn("missing target", errors[0])
        self.assertIn("missing fragment", errors[1])

    def test_invalid_reference_link_and_image_fail(self):
        errors = self.check("[ref][r]\n\n[r]: absent.md\n\n![image](absent.png)")
        self.assertEqual(2, len(errors))

    def test_code_examples_are_not_live_links(self):
        self.assertEqual([], self.check("```markdown\n[example](absent.md)\n```\n`[inline](absent.md)`"))

    def test_machine_local_paths_and_repository_escape_fail(self):
        errors = self.check("[local](file:///C:/work/a.md)\n[drive](C:/work/a.md)\n[out](../outside.md)")
        self.assertEqual(3, len(errors))
        self.assertIn("machine-specific", errors[0])
        self.assertIn("machine-specific", errors[1])
        self.assertIn("leaves the repository", errors[2])

    def test_remote_urls_are_not_fetched(self):
        self.assertEqual([], self.check("[remote](https://example.invalid/page#unknown)"))

    def test_same_page_fragment_and_directory_index(self):
        (self.root / "guide").mkdir()
        (self.root / "guide/index.md").write_text("# Guide", encoding="utf-8")
        self.assertEqual([], self.check("# Here\n[here](#here)\n[guide](guide/#guide)"))

    def test_nested_navigation_inventory(self):
        self.assertEqual(["index.md", "nested.md"], list(navigation_paths(
            [{"Home": "index.md"}, {"Guides": [{"Nested": "nested.md"}]}])))

    def test_historical_body_is_exempt_but_its_navigation_target_must_exist(self):
        docs = self.root / "docs"
        (docs / "dev/history").mkdir(parents=True)
        (docs / "index.md").write_text("# Home", encoding="utf-8")
        historical = docs / "dev/history/snapshot.md"
        historical.write_text("[quoted historical path](file:///C:/old/example.md)", encoding="utf-8")
        self.page.write_text("# README", encoding="utf-8")
        (self.root / "mkdocs.yml").write_text(
            "site_name: Test\nnav:\n  - Home: index.md\n  - History: dev/history/snapshot.md\n", encoding="utf-8")
        errors, count = documentation_link_errors(self.root)
        self.assertEqual([], errors)
        self.assertEqual(2, count)
        historical.unlink()
        errors, _ = documentation_link_errors(self.root)
        self.assertTrue(any("navigation target" in error for error in errors))

    def test_published_machine_path_reports_source_and_line(self):
        errors = self.check("# Guide\n\n[bad](file:///C:/old/example.md)")
        self.assertIn("README.md:3:", errors[0])

    def project(self, extra_config=""):
        docs = self.root / "docs"
        docs.mkdir(exist_ok=True)
        (docs / "index.md").write_text("# Home", encoding="utf-8")
        self.page.write_text("# README", encoding="utf-8")
        (self.root / "mkdocs.yml").write_text(
            "site_name: Test\nnav:\n  - Home: index.md\n" + extra_config, encoding="utf-8")
        return docs

    def test_new_orphan_fails(self):
        docs = self.project()
        (docs / "forgotten.md").write_text("# Forgotten", encoding="utf-8")
        errors, _ = documentation_link_errors(self.root)
        self.assertTrue(any("docs/forgotten.md: orphan/unclassified" in error for error in errors))

    def test_indexed_reference_is_maintained_and_checked(self):
        docs = self.project()
        (docs / "index.md").write_text("# Home\n[Reference](reference.md)", encoding="utf-8")
        reference = docs / "reference.md"
        reference.write_text("# Reference", encoding="utf-8")
        errors, count = documentation_link_errors(self.root)
        self.assertEqual([], errors)
        self.assertEqual(3, count)
        reference.write_text("[bad](missing.md)", encoding="utf-8")
        errors, _ = documentation_link_errors(self.root)
        self.assertTrue(any("missing target" in error for error in errors))

    def test_disconnected_link_cycle_does_not_establish_publication(self):
        docs = self.project()
        (docs / "a.md").write_text("[B](b.md)", encoding="utf-8")
        (docs / "b.md").write_text("[A](a.md)", encoding="utf-8")
        errors, _ = documentation_link_errors(self.root)
        self.assertEqual(2, len([e for e in errors if "orphan/unclassified" in e]))

    def test_explicit_history_proposals_and_excluded_notes_are_valid(self):
        docs = self.project("exclude_docs: |\n  notes/**\n")
        for name in ("dev/history/snapshot.md", "improvements/proposal.md", "notes/private.md"):
            path = docs / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("[historical example](file:///C:/example.md)", encoding="utf-8")
        self.assertEqual([], documentation_link_errors(self.root)[0])

    def test_historical_link_cannot_hide_current_orphan(self):
        docs = self.project()
        (docs / "dev/history").mkdir(parents=True)
        (docs / "dev/history/snapshot.md").write_text("[Current](../../orphan.md)", encoding="utf-8")
        (docs / "orphan.md").write_text("# Current", encoding="utf-8")
        errors, _ = documentation_link_errors(self.root)
        self.assertTrue(any("orphan/unclassified" in error for error in errors))

    def test_excluded_page_cannot_be_published_through_navigation_or_links(self):
        docs = self.project("  - Hidden: notes/private.md\nexclude_docs: |\n  notes/**\n")
        (docs / "notes").mkdir()
        (docs / "notes/private.md").write_text("# Private", encoding="utf-8")
        (docs / "index.md").write_text("[Hidden](notes/private.md)", encoding="utf-8")
        errors, _ = documentation_link_errors(self.root)
        self.assertTrue(any("navigation target" in e and "excluded" in e for e in errors))
        self.assertTrue(any("link to excluded page" in e for e in errors))

    def test_navigation_fragments_are_checked(self):
        self.project()
        config = self.root / "mkdocs.yml"
        text = config.read_text(encoding="utf-8")
        config.write_text(text.replace("index.md", "index.md#home"), encoding="utf-8")
        self.assertEqual([], documentation_link_errors(self.root)[0])
        config.write_text(text.replace("index.md", "index.md#absent"), encoding="utf-8")
        self.assertTrue(any("navigation fragment" in e for e in documentation_link_errors(self.root)[0]))


if __name__ == "__main__":
    unittest.main()
