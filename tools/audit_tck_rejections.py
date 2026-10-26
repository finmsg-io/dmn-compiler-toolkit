"""Join saved rejection diagnostics to pinned official cases and model evidence.

This evidence generator does not execute FEEL or establish runtime conformance.
The classifications below are bounded review rules for the current inventory;
unmatched cases fail closed and require review rather than extending an allowlist.
"""
import argparse
import collections
import functools
import hashlib
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "dmn-tck-runner/src/test/resources/tck-official/TestCases"
# Reviewed 2026-10-25 inventory; regeneration is not approval of new diagnostics.
REVIEWED_DIAGNOSTICS_SHA256 = "98efe93aca661b831af4cb2374701f5f91ce525c3fbbd3ca1797f6d4bf409035"


def sha(value):
    return hashlib.sha256(value).hexdigest()


@functools.lru_cache(None)
def xml(path):
    return ET.parse(path).getroot()


def local(node):
    return node.tag.rsplit("}", 1)[-1]


def review(folder, case, codes, description):
    if folder == "1120-feel-duration-function" and codes == {"DECISION_TYPE_MISMATCH"}:
        return "REQUIRES_EXECUTION", "Declared result-type mismatch does not test duration argument parsing/null behavior."
    if codes == {"INCOMPATIBLE_BRANCH_TYPES"} and folder in {
        "1151-boxed-filter", "1153-boxed-some", "1154-boxed-every"
    }:
        return "REQUIRES_EXECUTION", "Mixed branch types do not prove the error of an evaluated filter/quantifier predicate."
    allowed = {
        "0057-feel-context": {"DUPLICATE_NAME", "INVALID_PROPERTY_ACCESS"},
        "0071-feel-between": {"INVALID_BETWEEN_OPERAND"},
        "0075-feel-exponent": {"INVALID_OPERAND_TYPES"},
        "0082-feel-coercion": {"DECISION_TYPE_MISMATCH", "BKM_RETURN_TYPE_MISMATCH",
            "INVOCATION_ARGUMENT_TYPE_MISMATCH", "MISSING_INVOCATION_BINDING", "UNKNOWN_INVOCATION_PARAMETER"},
        "0099-arithmetic-negation": {"INVALID_OPERAND_TYPE"},
        "0100-arithmetic": {"INVALID_OPERAND_TYPES"},
        "1131-feel-function-invocation": {"UNKNOWN_FUNCTION", "UNKNOWN_NAME"},
        "1150-boxed-conditional": {"INVALID_OPERAND_TYPE"},
        "1151-boxed-filter": {"INVALID_FILTER_TYPE"},
        "1153-boxed-some": {"INVALID_OPERAND_TYPE"},
        "1154-boxed-every": {"INVALID_OPERAND_TYPE"},
    }
    if folder not in allowed or not codes or not codes <= allowed[folder]:
        raise ValueError(f"Unreviewed diagnostic: {folder}#{case}: {codes}")
    if folder == "0100-arithmetic":
        assert case.startswith("error_when_") and description == "invalid arithmetic combination"
    return "RELATED_STATIC_REJECTION", "Diagnostic targets the negative operation/type contract in this case; accepted as static negative evidence only, not runtime/SQL execution."


def generate(diagnostics):
    digest = sha(diagnostics.read_bytes().replace(b"\r\n", b"\n"))
    if digest != REVIEWED_DIAGNOSTICS_SHA256:
        raise ValueError("Diagnostics differ from the reviewed inventory; case-level review is required")
    records = []
    seen = set()
    for line in diagnostics.read_text(encoding="utf-8").splitlines():
        identity, diagnostic = line.split("\t", 1)
        key = identity.split(" @", 1)[0]
        assert key not in seen, f"Duplicate case: {key}"
        seen.add(key)
        entry, case_id = key.split("#", 1)
        test_path = FIXTURES / entry
        tests = xml(test_path)
        case = next(node for node in tests if local(node) == "testCase" and node.get("id") == case_id)
        results = [node for node in case if local(node) == "resultNode"]
        assert results and all(node.get("errorResult") == "true" for node in results), key
        names = sorted(node.get("name") for node in results)
        description = " ".join(" ".join(" ".join(node.itertext()).split()) for node in case if local(node) == "description")
        model = test_path.parent / next(node.text for node in tests if local(node) == "modelName")
        definitions = xml(model)
        nodes = {(local(node), key): node for node in definitions for key in (node.get("id"), node.get("name")) if key}
        evidence = []
        for kind, node_id in sorted(set(re.findall(r"definitions/(decision|businessKnowledgeModel)\[([^]]+)\]", diagnostic))):
            node = nodes[(kind, node_id)]
            if kind == "decision":
                assert node.get("name") in names, (key, node.get("name"), names)
            else:
                # The named BKM must be referenced by a requested decision.
                requested = [n for n in definitions if local(n) == "decision" and n.get("name") in names]
                assert any(node.get("name", "") in " ".join(n.itertext()) or any(
                    x.get("href") == "#" + node_id for x in n.iter()) for n in requested), key
            expressions = [" ".join(" ".join(n.itertext()).split()) for n in node.iter() if local(n) == "text"]
            evidence.append(f"{kind}[{node_id}] {node.get('name')}: " + " | ".join(expressions))
        assert evidence, key
        codes = set(re.findall(r"SEMANTIC_ANALYSIS/([A-Z_]+):", diagnostic))
        verdict, reason = review(test_path.parent.name, case_id, codes, description)
        fields = [key, verdict, "|".join(names), sha(model.read_bytes().replace(b"\r\n", b"\n")), sha(test_path.read_bytes().replace(b"\r\n", b"\n")),
            sha(diagnostic.encode("utf-8")), reason, description, " || ".join(evidence), diagnostic]
        assert all("\t" not in field and "\n" not in field and "\r" not in field for field in fields)
        records.append(fields)
    assert len(records) == 676, "Reviewed inventory size changed"
    return sorted(records)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("diagnostics", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    records = generate(args.diagnostics)
    header = "case\tclassification\tresults\tmodel_sha256\ttest_sha256\tdiagnostic_sha256\tjustification\tcase_description\tmodel_evidence\tdiagnostic"
    args.output.write_text(header + "\n" + "\n".join("\t".join(row) for row in records) + "\n", encoding="utf-8")
    print(dict(collections.Counter(row[1] for row in records)))
    print(f"{len(records)} unique cases; source paths and error-expecting requested results verified; no FEEL/SQL executed.")


if __name__ == "__main__":
    main()
