"""Local links for maintained guidance; navigation targets are always checked.

Historical delivery records, proposals and immutable ADRs retain their original
examples. Their targets may be linked from current pages, but their own bodies
are outside this check. See docs/documentation-structure.md for scope.
"""

from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

import markdown
from mkdocs.config import load_config


HISTORICAL_PREFIXES = ("dev/slices/", "dev/history/", "improvements/", "architecture/adr/")
MAINTAINED_INDEXES = ("dev/slices/index.md", "architecture/adr/generall-adr.md")


class Links(HTMLParser):
    def __init__(self, rendered: str):
        super().__init__()
        self.targets = []
        self.anchors = set()
        self.feed(rendered)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            self.anchors.add(attrs["id"])
        if tag == "a" and "name" in attrs:
            self.anchors.add(attrs["name"])
        target = attrs.get("href") if tag == "a" else attrs.get("src") if tag == "img" else None
        if target:
            self.targets.append(target)


def navigation_paths(nav):
    if isinstance(nav, str):
        yield nav
    elif isinstance(nav, list):
        for item in nav:
            yield from navigation_paths(item)
    elif isinstance(nav, dict):
        for item in nav.values():
            yield from navigation_paths(item)


def check_links(root: Path, pages, extensions, extension_configs) -> list[str]:
    cache = {}
    sources = {}
    errors = []

    def parsed(path):
        if path not in cache:
            engine = markdown.Markdown(extensions=extensions, extension_configs=extension_configs)
            text = path.read_text(encoding="utf-8")
            sources[path] = text.splitlines()
            cache[path] = Links(engine.convert(text))
        return cache[path]

    for page in pages:
        if not page.is_file():
            errors.append(f"{page.relative_to(root)}: missing maintained page")
            continue
        for target in parsed(page).targets:
            url = urlsplit(target)
            line = next((i for i, text in enumerate(sources[page], 1)
                         if target in text), None)
            location = f"{page.relative_to(root)}" + (f":{line}" if line else "")
            label = f"{location}: link {target!r}"
            if url.scheme.lower() == "file" or (len(url.scheme) == 1 and target[1:2] == ":"):
                errors.append(f"{label}: machine-specific local path")
                continue
            if url.scheme or url.netloc:
                continue  # No network requests; remote URLs are not validated.
            # Repository-root / links are ambiguous between GitHub and the site.
            if url.path.startswith("/"):
                errors.append(f"{label}: use a portable relative path")
                continue
            path = (page.parent / unquote(url.path)).resolve() if url.path else page
            if not path.is_relative_to(root):
                errors.append(f"{label}: target leaves the repository")
                continue
            if path.is_dir():
                path = path / "index.md"
            if not path.is_file():
                errors.append(f"{label}: missing target")
                continue
            if url.fragment and path.suffix.lower() == ".md":
                if unquote(url.fragment) not in parsed(path).anchors:
                    errors.append(f"{label}: missing fragment")
    return errors


def documentation_link_errors(root: Path) -> tuple[list[str], int]:
    config = load_config(str(root / "mkdocs.yml"))
    docs = Path(config["docs_dir"])
    errors = []
    pages = {root / "README.md"}
    for target in navigation_paths(config["nav"]):
        if urlsplit(target).scheme:
            continue
        path = (docs / target).resolve()
        if not path.is_relative_to(docs) or not path.is_file():
            errors.append(f"mkdocs.yml: missing or invalid navigation target {target!r}")
        elif target in MAINTAINED_INDEXES or not target.startswith(HISTORICAL_PREFIXES):
            pages.add(path)
    errors.extend(check_links(root, sorted(pages), config["markdown_extensions"], config["mdx_configs"]))
    return errors, len(pages)
