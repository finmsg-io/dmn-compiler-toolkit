"""Fail CI when canonical documentation facts drift from repository metadata."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


def reactor_modules() -> list[str]:
    root = ET.parse(ROOT / "pom.xml").getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    return [node.text for node in root.findall("./m:modules/m:module", namespace) if node.text]


def documented_modules() -> list[str]:
    text = (ROOT / "docs" / "modules.md").read_text(encoding="utf-8")
    return re.findall(r"^## `([^`]+)`$", text, flags=re.MULTILINE)


def main() -> int:
    errors: list[str] = []
    expected = reactor_modules()
    documented = documented_modules()
    if documented != expected:
        errors.append(
            "docs/modules.md module order does not match pom.xml:\n"
            f"  pom.xml: {expected}\n  docs:    {documented}"
        )

    living_pages = [
        ROOT / "docs" / "index.md",
        ROOT / "docs" / "modules.md",
        ROOT / "docs" / "todos" / "index.md",
    ]
    forbidden = re.compile(r"\bTCK certified\b|\bOMG certified\b", re.IGNORECASE)
    for page in living_pages:
        for line_number, line in enumerate(page.read_text(encoding="utf-8").splitlines(), 1):
            if forbidden.search(line):
                errors.append(f"{page.relative_to(ROOT)}:{line_number}: unsupported certification claim")

    if errors:
        print("Documentation verification failed:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print(f"Documentation facts verified for {len(expected)} reactor modules.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
