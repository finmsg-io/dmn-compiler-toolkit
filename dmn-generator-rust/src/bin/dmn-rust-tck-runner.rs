use std::collections::BTreeMap;
use std::env;
use std::error::Error;
use std::fs::{self, File};
use std::io::{BufRead, BufReader, Write};
use std::path::PathBuf;
use std::process::Command;

const HARNESS_PRELUDE: &str = r#"
use generated_dmn::{Value, evaluate};

fn equivalent(actual: &Value, expected: &Value) -> bool {
    if actual == expected {
        return true;
    }
    match (actual, expected) {
        (
            Value::Number { coefficient: a, scale: a_scale },
            Value::Number { coefficient: b, scale: b_scale },
        ) => {
            normalize_decimal(a, *a_scale) == normalize_decimal(b, *b_scale)
        }
        (Value::List(a), Value::List(b)) => {
            a.len() == b.len() && a.iter().zip(b.iter()).all(|(x, y)| equivalent(x, y))
        }
        (Value::Record(a), Value::Record(b)) => {
            a.len() == b.len() && a.iter().all(|(k, v)| b.get(k).map_or(false, |bv| equivalent(v, bv)))
        }
        _ => false,
    }
}

fn normalize_decimal(coefficient: &str, scale: i32) -> (String, i32) {
    if coefficient == "0" || coefficient == "-0" {
        return ("0".to_string(), 0);
    }
    let is_neg = coefficient.starts_with('-');
    let digits = coefficient.trim_start_matches('-');
    let mut s = digits.to_string();
    let mut scale = scale;
    while s.ends_with('0') && scale > 0 {
        s.pop();
        scale -= 1;
    }
    while scale < 0 {
        s.push('0');
        scale += 1;
    }
    let coef = if is_neg { format!("-{}", s) } else { s };
    (coef, scale)
}
"#;

struct CaseAccounting {
    entry_id: String,
    case_id: String,
    group: String,
    category: String,
    outcome: String,
    diagnostic: String,
}

// A successful process can run zero tests. Only an explicit result for this
// case is evidence that its assertions ran.
fn case_result(output: &str, index: usize) -> (&'static str, &'static str) {
    let name = format!("case_{index:05}");
    let result = output
        .lines()
        .find_map(|line| line.trim().strip_prefix(&format!("test {name} ... ")));
    match result {
        Some("ok") => ("PASS", "PASS"),
        Some("FAILED") => {
            let marker = format!("---- {name} stdout ----");
            let detail = output
                .split_once(&marker)
                .map(|(_, rest)| {
                    rest.split("\n---- ")
                        .next()
                        .unwrap_or(rest)
                        .split("\nfailures:")
                        .next()
                        .unwrap_or(rest)
                })
                .unwrap_or("");
            if detail.contains("RUST_TCK_EXECUTION_ERROR") {
                ("EXECUTION_ERROR", "EXECUTION_ERROR")
            } else {
                ("FAILURE", "FAILURE")
            }
        }
        _ => ("EXECUTION_ERROR", "TEST_NOT_EXECUTED"),
    }
}

fn main() -> Result<(), Box<dyn Error>> {
    let audit_dir = env::args()
        .nth(1)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("target/rust-tck-audit-2026-11-14"));

    let manifest_path = audit_dir.join("manifest.tsv");
    if !manifest_path.exists() {
        return Err(format!("Manifest not found at {}", manifest_path.display()).into());
    }

    let manifest_file = File::open(&manifest_path)?;
    let reader = BufReader::new(manifest_file);

    let mut lines = reader.lines();
    let _header = lines.next().ok_or("Empty manifest")??;

    let mut cases: Vec<CaseAccounting> = Vec::new();
    let mut ready_groups: BTreeMap<String, Vec<usize>> = BTreeMap::new();

    for line in lines {
        let line = line?;
        let parts: Vec<&str> = line.split('\t').collect();
        if parts.len() != 5 || parts[0].is_empty() || parts[1].is_empty() {
            return Err(format!("Invalid manifest row: {}", cases.len() + 2).into());
        }
        let entry_id = parts[0].to_string();
        let case_id = parts[1].to_string();
        let status = parts[2].to_string();
        let group = parts.get(3).unwrap_or(&"").to_string();
        let diagnostic = parts.get(4).unwrap_or(&"").to_string();

        let index = cases.len();
        if status == "READY" {
            ready_groups.entry(group.clone()).or_default().push(index);
            cases.push(CaseAccounting {
                entry_id,
                case_id,
                group,
                category: String::new(),
                outcome: String::new(),
                diagnostic,
            });
        } else {
            let category = "EXPORT_REJECTION".to_string();
            let outcome = status;
            cases.push(CaseAccounting {
                entry_id,
                case_id,
                group,
                category,
                outcome,
                diagnostic,
            });
        }
    }

    let total_cases = cases.len();
    println!(
        "Loaded manifest with {} cases from {}",
        total_cases,
        manifest_path.display()
    );
    println!(
        "Ready groups to evaluate in Rust: {} ({} cases)\n",
        ready_groups.len(),
        ready_groups.values().map(|v| v.len()).sum::<usize>()
    );

    let cargo_bin = env::var("CARGO").unwrap_or_else(|_| "cargo".to_string());
    let generator_bin = env::current_exe()?
        .parent()
        .map(|p| p.join("dmn-generator-rust.exe"))
        .filter(|p| p.exists())
        .unwrap_or_else(|| PathBuf::from("dmn-generator-rust/target/debug/dmn-generator-rust.exe"));

    let mut group_num = 0;
    for (group_id, indices) in &ready_groups {
        group_num += 1;
        print!(
            "[{}/{}] Evaluating group {} ({} cases)... ",
            group_num,
            ready_groups.len(),
            group_id,
            indices.len()
        );
        std::io::stdout().flush().ok();

        let group_dir = audit_dir.join(group_id);
        let artifact_path = group_dir.join("artifact.dmnir");
        let cases_path = group_dir.join("cases.rs");
        let crate_dir = group_dir.join("crate");

        if crate_dir.exists() {
            return Err(format!(
                "Output already exists; use a new audit directory: {}",
                crate_dir.display()
            )
            .into());
        }

        // 1. Generate crate
        let gen_out = Command::new(&generator_bin)
            .arg(&artifact_path)
            .arg(&crate_dir)
            .output();

        match gen_out {
            Ok(output) if !output.status.success() => {
                let err = String::from_utf8_lossy(&output.stderr).trim().to_string();
                let err = if err.is_empty() {
                    String::from_utf8_lossy(&output.stdout).trim().to_string()
                } else {
                    err
                };
                println!("RUST_REJECTION");
                for &idx in indices {
                    cases[idx].category = "RUST_REJECTION".to_string();
                    cases[idx].outcome = "RUST_REJECTION".to_string();
                    cases[idx].diagnostic = err.replace('\t', " ").replace('\n', " ");
                }
                continue;
            }
            Err(e) => {
                println!("GENERATOR_SPAWN_ERROR: {}", e);
                for &idx in indices {
                    cases[idx].category = "RUST_REJECTION".to_string();
                    cases[idx].outcome = "GENERATOR_ERROR".to_string();
                    cases[idx].diagnostic = e.to_string();
                }
                continue;
            }
            _ => {}
        }

        // 2. Add test harness
        let tests_dir = crate_dir.join("tests");
        fs::create_dir_all(&tests_dir)?;
        let cases_src = fs::read_to_string(&cases_path)?;
        fs::write(
            tests_dir.join("tck.rs"),
            format!("{}\n{}", HARNESS_PRELUDE, cases_src),
        )?;

        // 3. Compile tests
        let build_out = Command::new(&cargo_bin)
            .args(["test", "--offline", "--no-run"])
            .current_dir(&crate_dir)
            .output();

        match build_out {
            Ok(output) if !output.status.success() => {
                let err = String::from_utf8_lossy(&output.stderr).trim().to_string();
                println!("BUILD_ERROR");
                for &idx in indices {
                    cases[idx].category = "BUILD_ERROR".to_string();
                    cases[idx].outcome = "BUILD_ERROR".to_string();
                    cases[idx].diagnostic = err.replace('\t', " ").replace('\n', " ");
                }
                continue;
            }
            Err(e) => {
                println!("CARGO_BUILD_ERROR: {}", e);
                for &idx in indices {
                    cases[idx].category = "BUILD_ERROR".to_string();
                    cases[idx].outcome = "BUILD_ERROR".to_string();
                    cases[idx].diagnostic = e.to_string();
                }
                continue;
            }
            _ => {}
        }

        // 4. Run tests
        let test_out = Command::new(&cargo_bin)
            .args(["test", "--offline", "--", "--color", "never"])
            .current_dir(&crate_dir)
            .output();

        let (out_str, err_str, exit_code) = match test_out {
            Ok(o) => (
                String::from_utf8_lossy(&o.stdout).to_string(),
                String::from_utf8_lossy(&o.stderr).to_string(),
                o.status.code().unwrap_or(-1),
            ),
            Err(e) => (String::new(), e.to_string(), -1),
        };

        let full_output = format!("{}\n{}", out_str, err_str);
        fs::write(group_dir.join("rust-test.log"), &full_output)?;
        let mut passed = 0;
        let mut failed = 0;

        for &idx in indices {
            let (category, outcome) = case_result(&full_output, idx);
            cases[idx].category = category.to_string();
            cases[idx].outcome = outcome.to_string();
            if category == "PASS" {
                cases[idx].diagnostic = String::new();
                passed += 1;
            } else if category == "EXECUTION_ERROR" {
                cases[idx].diagnostic =
                    format!("{outcome}; process exit {exit_code}; see rust-test.log");
                failed += 1;
            } else {
                cases[idx].category = "FAILURE".to_string();
                cases[idx].outcome = "FAILURE".to_string();
                cases[idx].diagnostic = "Expected result differs".to_string();
                failed += 1;
            }
        }

        if failed == 0 {
            println!("PASS ({} cases)", passed);
        } else {
            println!("COMPLETED (passed={}, failed={})", passed, failed);
        }
    }

    // 5. Produce ledger and summary
    let mut category_counts: BTreeMap<String, usize> = BTreeMap::new();
    let mut outcome_counts: BTreeMap<String, usize> = BTreeMap::new();

    for case in &cases {
        *category_counts.entry(case.category.clone()).or_default() += 1;
        *outcome_counts.entry(case.outcome.clone()).or_default() += 1;
    }

    println!("\n================ FINAL ACCOUNTING SUMMARY ================");
    println!("Total accounted cases: {} / {}", cases.len(), total_cases);
    println!("\nBy Primary Bucket:");
    for cat in [
        "EXPORT_REJECTION",
        "RUST_REJECTION",
        "BUILD_ERROR",
        "EXECUTION_ERROR",
        "FAILURE",
        "PASS",
    ] {
        println!("  {:20}: {}", cat, category_counts.get(cat).unwrap_or(&0));
    }

    println!("\nDetailed Outcomes:");
    for (outcome, count) in &outcome_counts {
        println!("  {:30}: {}", outcome, count);
    }

    let tsv_path = audit_dir.join("rust-tck-accounting.tsv");
    let mut tsv = File::create(&tsv_path)?;
    writeln!(tsv, "entryId\tcaseId\tgroup\tcategory\toutcome\tdiagnostic")?;
    for case in &cases {
        writeln!(
            tsv,
            "{}\t{}\t{}\t{}\t{}\t{}",
            case.entry_id, case.case_id, case.group, case.category, case.outcome, case.diagnostic
        )?;
    }

    println!("\nLedger successfully written to: {}", tsv_path.display());
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::case_result;

    #[test]
    fn successful_process_without_case_is_not_a_pass() {
        assert_eq!(
            case_result("test result: ok. 0 passed; 0 failed", 1),
            ("EXECUTION_ERROR", "TEST_NOT_EXECUTED")
        );
        assert_eq!(
            case_result("test case_00010 ... ok", 1),
            ("EXECUTION_ERROR", "TEST_NOT_EXECUTED")
        );
        assert_eq!(
            case_result("test case_00001 ... ignored", 1),
            ("EXECUTION_ERROR", "TEST_NOT_EXECUTED")
        );
        assert_eq!(case_result("test case_00001 ... ok", 1), ("PASS", "PASS"));
    }

    #[test]
    fn failures_are_classified_per_case() {
        let output = "test case_00001 ... FAILED\ntest case_00002 ... FAILED\ntest case_00003 ... ok\n\n---- case_00001 stdout ----\nExpected result differs\n\n---- case_00002 stdout ----\nRUST_TCK_EXECUTION_ERROR: invalid input\n\nfailures:\ncase_00001\ncase_00002\n";
        assert_eq!(case_result(output, 1), ("FAILURE", "FAILURE"));
        assert_eq!(
            case_result(output, 2),
            ("EXECUTION_ERROR", "EXECUTION_ERROR")
        );
        assert_eq!(case_result(output, 3), ("PASS", "PASS"));
    }
}
