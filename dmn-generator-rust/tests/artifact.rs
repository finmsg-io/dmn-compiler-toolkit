use std::fs;
use std::path::Path;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

const FIXTURE: &[u8] = include_bytes!("fixtures/scalar-0.0.dmnir");
const GENERATOR: &str = env!("CARGO_BIN_EXE_dmn-generator-rust");

#[test]
fn java_artifact_generates_deterministic_offline_rust() {
    verify_parity(
        "scalar",
        FIXTURE,
        include_str!("fixtures/generated_parity.rs.txt"),
        &[],
    );
}

#[test]
fn java_expression_artifact_generates_deterministic_offline_rust() {
    verify_parity(
        "expressions",
        include_bytes!("fixtures/comparisons-0.1.dmnir"),
        include_str!("fixtures/expression_parity.rs.txt"),
        &[],
    );
}

#[test]
fn java_arithmetic_artifact_generates_deterministic_offline_rust() {
    verify_parity(
        "arithmetic",
        include_bytes!("fixtures/arithmetic-0.1.dmnir"),
        include_str!("fixtures/arithmetic_parity.rs.txt"),
        &[],
    );
}

#[test]
fn java_multiplication_artifact_generates_deterministic_offline_rust() {
    verify_parity(
        "multiplication",
        include_bytes!("fixtures/multiplication-0.1.dmnir"),
        include_str!("fixtures/multiplication_parity.rs.txt"),
        &[],
    );
}

#[test]
fn java_division_artifact_generates_deterministic_offline_rust() {
    verify_parity(
        "division",
        include_bytes!("fixtures/division-0.1.dmnir"),
        include_str!("fixtures/division_parity.rs.txt"),
        &[(
            "division_vectors.tsv",
            include_str!("fixtures/division_vectors.tsv"),
        )],
    );
}

fn verify_parity(label: &str, fixture: &[u8], test: &str, data: &[(&str, &str)]) {
    let directory = scratch(label);
    let artifact = directory.join("input.dmnir");
    fs::write(&artifact, fixture).unwrap();
    let first = directory.join("first");
    let second = directory.join("second");
    for output in [&first, &second] {
        let result = Command::new(GENERATOR)
            .args([&artifact, output])
            .output()
            .unwrap();
        assert!(
            result.status.success(),
            "{}",
            String::from_utf8_lossy(&result.stderr)
        );
    }
    for file in ["Cargo.toml", "lib.rs"] {
        assert_eq!(
            fs::read(first.join(file)).unwrap(),
            fs::read(second.join(file)).unwrap()
        );
    }
    fs::create_dir(first.join("tests")).unwrap();
    fs::write(first.join("tests/parity.rs"), test).unwrap();
    for (name, content) in data {
        fs::write(first.join("tests").join(name), content).unwrap();
    }
    let result = Command::new(env!("CARGO"))
        .args(["test", "--offline", "--quiet"])
        .current_dir(&first)
        .output()
        .unwrap();
    assert!(
        result.status.success(),
        "{}",
        String::from_utf8_lossy(&result.stderr)
    );
}

#[test]
fn rejects_bad_and_unsupported_artifacts_before_writing() {
    let directory = scratch("rejections");
    let mut duplicate = FIXTURE.to_vec();
    duplicate.extend(FIXTURE);
    let oversized = vec![0; 4 * 1024 * 1024 + 1];
    let power = include_bytes!("fixtures/unsupported-power-0.1.dmnir");
    for (label, bytes) in [
        ("truncated", [0x0a, 0x10].as_slice()),
        ("duplicate", duplicate.as_slice()),
        ("oversized", oversized.as_slice()),
        ("power", power.as_slice()),
    ] {
        let artifact = directory.join(format!("{label}.dmnir"));
        let output = directory.join(label);
        fs::write(&artifact, bytes).unwrap();
        let result = Command::new(GENERATOR)
            .args([&artifact, &output])
            .output()
            .unwrap();
        assert!(!result.status.success(), "{label} was accepted");
        assert!(!output.exists(), "{label} produced output");
    }
}

fn scratch(label: &str) -> std::path::PathBuf {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("target/artifact-tests");
    fs::create_dir_all(&root).unwrap();
    let nonce = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    let path = root.join(format!("{label}-{}-{nonce}", std::process::id()));
    fs::create_dir(&path).unwrap();
    path
}
