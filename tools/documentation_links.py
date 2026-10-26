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


def publication_pages(root: Path, config) -> tuple[list[str], set[Path], dict[str, int]]:
    """Classify every Markdown page; only reachable current guidance is maintained."""
    docs = Path(config["docs_dir"]).resolve()
    excluded = config["exclude_docs"]
    inventory = {path.resolve(): path.relative_to(docs).as_posix() for path in docs.rglob("*.md")}
    errors = []
    maintained = {root / "README.md"}
    navigation = set()
    for target in navigation_paths(config["nav"]):
        url = urlsplit(target)
        if url.scheme or url.netloc:
            continue
        path = (docs / unquote(url.path)).resolve()
        if not path.is_relative_to(docs) or not path.is_file():
            errors.append(f"mkdocs.yml: missing or invalid navigation target {target!r}")
            continue
        name = path.relative_to(docs).as_posix()
        if excluded is not None and excluded.match_file(name):
            errors.append(f"mkdocs.yml: navigation target {target!r} is excluded from publication")
            continue
        navigation.add(path)
        if name in MAINTAINED_INDEXES or not name.startswith(HISTORICAL_PREFIXES):
            maintained.add(path)
        if url.fragment:
            engine = markdown.Markdown(extensions=config["markdown_extensions"],
                                       extension_configs=config["mdx_configs"])
            if unquote(url.fragment) not in Links(engine.convert(path.read_text(encoding="utf-8"))).anchors:
                errors.append(f"mkdocs.yml: missing navigation fragment in {target!r}")

    # Starting from real entry pages prevents disconnected cycles from qualifying
    # as indexed references. Do not follow historical links into current guidance.
    pending = list(maintained)
    while pending:
        page = pending.pop()
        if not page.is_file():
            continue
        engine = markdown.Markdown(extensions=config["markdown_extensions"],
                                   extension_configs=config["mdx_configs"])
        for target in Links(engine.convert(page.read_text(encoding="utf-8"))).targets:
            url = urlsplit(target)
            if url.scheme or url.netloc or not url.path or url.path.startswith("/"):
                continue
            path = (page.parent / unquote(url.path)).resolve()
            if path.is_dir():
                path /= "index.md"
            if path not in inventory or path in maintained:
                continue
            name = inventory[path]
            if excluded is not None and excluded.match_file(name):
                errors.append(f"{page.relative_to(root)}: link to excluded page {target!r}")
            elif name in MAINTAINED_INDEXES or not name.startswith(HISTORICAL_PREFIXES):
                maintained.add(path)
                pending.append(path)

    counts = {"navigation guidance": 0, "indexed guidance": 0, "historical/ADR": 0,
              "proposal/example": 0, "excluded": 0, "unclassified": 0}
    for path, name in inventory.items():
        if excluded is not None and excluded.match_file(name):
            category = "excluded"
        elif path in maintained:
            category = "navigation guidance" if path in navigation else "indexed guidance"
        elif name.startswith("improvements/"):
            category = "proposal/example"
        elif name.startswith(HISTORICAL_PREFIXES) and name not in MAINTAINED_INDEXES:
            category = "historical/ADR"
        else:
            category = "unclassified"
            errors.append(f"docs/{name}: orphan/unclassified page; add navigation, a maintained index link, "
                          "or an explicit historical/proposal/exclusion classification")
        counts[category] += 1
    return errors, maintained, counts


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
    errors, pages, counts = publication_pages(root, config)
    errors.extend(check_links(root, sorted(pages), config["markdown_extensions"], config["mdx_configs"]))
    return errors, len(pages)
