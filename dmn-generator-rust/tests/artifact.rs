use std::fs;
use std::path::Path;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

const FIXTURE: &[u8] = include_bytes!("fixtures/scalar-0.0.dmnir");
const GENERATOR: &str = env!("CARGO_BIN_EXE_dmn-generator-rust");

#[test]
fn java_artifact_generates_deterministic_offline_rust() {
    let directory = scratch("parity");
    let artifact = directory.join("input.dmnir");
    fs::write(&artifact, FIXTURE).unwrap();
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
    fs::write(
        first.join("tests/parity.rs"),
        include_str!("fixtures/generated_parity.rs.txt"),
    )
    .unwrap();
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
    let higher = include_bytes!("fixtures/expressions-0.1.dmnir");
    for (label, bytes) in [
        ("truncated", [0x0a, 0x10].as_slice()),
        ("duplicate", duplicate.as_slice()),
        ("oversized", oversized.as_slice()),
        ("higher", higher.as_slice()),
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
