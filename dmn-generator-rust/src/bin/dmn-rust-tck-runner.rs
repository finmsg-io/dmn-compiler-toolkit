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
    let result = output.lines().find_map(|line| {
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix(&format!("test {name} ... ")) {
            Some(rest)
        } else if let Some(idx) = trimmed.find(&format!("::{name} ... ")) {
            Some(&trimmed[idx + name.len() + 7..])
        } else {
            None
        }
    });
    match result {
        Some("ok") => ("PASS", "PASS"),
        Some("FAILED") => {
            let marker1 = format!("---- {name} stdout ----");
            let marker2 = format!("::{name} stdout ----");
            let detail = output
                .split_once(&marker1)
                .or_else(|| output.split_once(&marker2))
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
    let audit_dir = std::path::absolute(&audit_dir)?;

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

    let num_threads = std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(8)
        .min(16);
    println!("Evaluating with {} parallel worker threads...", num_threads);

    let ready_list: Vec<(String, Vec<usize>)> = ready_groups.into_iter().collect();
    let total_ready = ready_list.len();

    struct EvaluationResult {
        indices: Vec<usize>,
        category: String,
        outcome: String,
        diagnostic: String,
    }

    struct GroupOutcome {
        results: Vec<EvaluationResult>,
    }

    let group_outcomes = std::sync::Arc::new(std::sync::Mutex::new(Vec::new()));
    let successfully_generated = std::sync::Arc::new(std::sync::Mutex::new(Vec::new()));

    // Phase 1: Generate all crates in parallel
    println!(
        "Phase 1: Generating Rust crates for {} ready groups in parallel...",
        total_ready
    );
    let gen_queue = std::sync::Arc::new(std::sync::Mutex::new(ready_list.into_iter()));
    let mut gen_handles = Vec::new();

    for _ in 0..num_threads {
        let queue = std::sync::Arc::clone(&gen_queue);
        let group_outcomes = std::sync::Arc::clone(&group_outcomes);
        let successfully_generated = std::sync::Arc::clone(&successfully_generated);
        let audit_dir = audit_dir.clone();
        let generator_bin = generator_bin.clone();

        gen_handles.push(std::thread::spawn(
            move || -> Result<(), Box<dyn Error + Send + Sync>> {
                loop {
                    let item = {
                        let mut lock = queue.lock().unwrap();
                        lock.next()
                    };
                    let Some((group_id, indices)) = item else {
                        break;
                    };

                    let group_dir = audit_dir.join(&group_id);
                    let artifact_path = group_dir.join("artifact.dmnir");
                    let cases_path = group_dir.join("cases.rs");
                    let crate_dir = group_dir.join("crate");
                    if crate_dir.exists() {
                        let _ = fs::remove_dir_all(&crate_dir);
                    }

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
                            let diag = err.replace('\t', " ").replace('\n', " ");
                            let results = indices
                                .iter()
                                .map(|&idx| EvaluationResult {
                                    indices: vec![idx],
                                    category: "RUST_REJECTION".to_string(),
                                    outcome: "RUST_REJECTION".to_string(),
                                    diagnostic: diag.clone(),
                                })
                                .collect();
                            group_outcomes
                                .lock()
                                .unwrap()
                                .push(GroupOutcome { results });
                            continue;
                        }
                        Err(e) => {
                            let diag = e.to_string();
                            let results = indices
                                .iter()
                                .map(|&idx| EvaluationResult {
                                    indices: vec![idx],
                                    category: "RUST_REJECTION".to_string(),
                                    outcome: "GENERATOR_ERROR".to_string(),
                                    diagnostic: diag.clone(),
                                })
                                .collect();
                            group_outcomes
                                .lock()
                                .unwrap()
                                .push(GroupOutcome { results });
                            continue;
                        }
                        _ => {}
                    }

                    // Add standalone test harness in crate/tests/tck.rs
                    let tests_dir = crate_dir.join("tests");
                    fs::create_dir_all(&tests_dir)?;
                    let cases_src = fs::read_to_string(&cases_path)?;
                    fs::write(
                        tests_dir.join("tck.rs"),
                        format!("{}\n{}", HARNESS_PRELUDE, cases_src),
                    )?;

                    successfully_generated
                        .lock()
                        .unwrap()
                        .push((group_id, indices));
                }
                Ok(())
            },
        ));
    }

    for handle in gen_handles {
        handle.join().unwrap().map_err(|e| e.to_string())?;
    }

    let mut generated_groups = std::mem::take(&mut *successfully_generated.lock().unwrap());
    generated_groups.sort_by(|a, b| a.0.cmp(&b.0));
    println!(
        "Phase 1 complete: {} groups successfully generated.",
        generated_groups.len()
    );

    // Phase 2: Fast unified test execution
    let mut fallback_groups = Vec::new();
    // Bound compiler memory and temporary disk use for generated runtimes.
    for (batch_index, batch) in generated_groups.chunks(32).enumerate() {
        let generated_groups = batch.to_vec();
        println!(
            "Phase 2: Compiling and executing batch {} ({} groups)...",
            batch_index + 1,
            batch.len()
        );
        let harness_dir = audit_dir.join("unified_harness");
        let src_dir = harness_dir.join("src");
        fs::create_dir_all(&src_dir)?;

        fs::write(
            harness_dir.join("Cargo.toml"),
            r#"[package]
name = "unified-harness"
version = "0.1.0"
edition = "2024"
publish = false

[profile.test]
debug = false
opt-level = 0
"#,
        )?;

        // Write shared prelude (excluding external crate import for unified module layout)
        let unified_prelude = HARNESS_PRELUDE
            .lines()
            .filter(|l| !l.contains("use generated_dmn"))
            .collect::<Vec<_>>()
            .join("\n");
        fs::write(src_dir.join("prelude.rs"), unified_prelude)?;

        // Build lib.rs modules
        let mut lib_src = String::from(
            "#![allow(unused_imports, dead_code, unused_variables, non_snake_case, non_camel_case_types)]\n\n",
        );
        for (group_id, _) in &generated_groups {
            let group_dir = audit_dir.join(group_id);
            let lib_clean = group_dir
                .join("crate")
                .join("lib.rs")
                .to_string_lossy()
                .replace('\\', "/");
            let cases_clean = group_dir
                .join("cases.rs")
                .to_string_lossy()
                .replace('\\', "/");

            lib_src.push_str(&format!(
                "pub mod {} {{\n    include!(\"{}\");\n    include!(\"prelude.rs\");\n    include!(\"{}\");\n}}\n\n",
                group_id, lib_clean, cases_clean
            ));
        }
        fs::write(src_dir.join("lib.rs"), &lib_src)?;

        let test_start = std::time::Instant::now();
        let mut attempts = 0;
        let (out_str, err_str, exit_code) = loop {
            attempts += 1;
            let test_out = Command::new(&cargo_bin)
                .env("CARGO_INCREMENTAL", "0")
                .args(["test", "--lib", "--offline", "--", "--color", "never"])
                .current_dir(&harness_dir)
                .output();

            let (out, err, code) = match test_out {
                Ok(o) => (
                    String::from_utf8_lossy(&o.stdout).to_string(),
                    String::from_utf8_lossy(&o.stderr).to_string(),
                    o.status.code().unwrap_or(-1),
                ),
                Err(e) => (String::new(), e.to_string(), -1),
            };

            if code != 0
                && attempts < 4
                && (err.contains("Access is denied")
                    || err.contains("os error 5")
                    || err.contains("os error 32"))
            {
                std::thread::sleep(std::time::Duration::from_millis(200 * attempts));
                continue;
            }
            break (out, err, code);
        };

        let full_output = format!("{}\n{}", out_str, err_str);
        let is_build_error = exit_code != 0
            && (err_str.contains("error[E") || err_str.contains("could not compile"));

        fs::write(
            audit_dir.join(format!("rust-batch-{:03}.log", batch_index + 1)),
            &full_output,
        )?;
        if !is_build_error && full_output.contains("test result:") {
            let elapsed = test_start.elapsed();
            println!(
                "Unified harness executed in {:.2}s. Parsing results...",
                elapsed.as_secs_f64()
            );
            for (group_id, indices) in &generated_groups {
                let mut passed = 0;
                let mut failed = 0;
                let mut results = Vec::new();

                for &idx in indices {
                    let (category, outcome) = case_result(&full_output, idx);
                    let (cat, out, diag) = if category == "PASS" {
                        passed += 1;
                        ("PASS".to_string(), outcome.to_string(), String::new())
                    } else if category == "EXECUTION_ERROR" {
                        failed += 1;
                        (
                            "EXECUTION_ERROR".to_string(),
                            outcome.to_string(),
                            format!("{outcome}; see unified harness output"),
                        )
                    } else {
                        failed += 1;
                        (
                            "FAILURE".to_string(),
                            "FAILURE".to_string(),
                            "Expected result differs".to_string(),
                        )
                    };
                    results.push(EvaluationResult {
                        indices: vec![idx],
                        category: cat,
                        outcome: out,
                        diagnostic: diag,
                    });
                }

                let summary = if failed == 0 {
                    format!("PASS ({} cases)", passed)
                } else {
                    format!("COMPLETED (passed={}, failed={})", passed, failed)
                };
                println!(
                    "Evaluated group {} ({} cases): {}",
                    group_id,
                    indices.len(),
                    summary
                );
                group_outcomes
                    .lock()
                    .unwrap()
                    .push(GroupOutcome { results });
            }
        } else {
            println!(
                "Unified harness failed with exit_code={}: stdout:\n{}\nstderr:\n{}",
                exit_code, out_str, err_str
            );
            println!("Falling back to per-group evaluation...");
            fallback_groups.extend(generated_groups);
        }
    }

    if !fallback_groups.is_empty() {
        // Fallback: per-group execution
        let fallback_queue =
            std::sync::Arc::new(std::sync::Mutex::new(fallback_groups.into_iter()));
        let completed_counter = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let mut handles = Vec::new();

        for _ in 0..num_threads {
            let queue = std::sync::Arc::clone(&fallback_queue);
            let group_outcomes = std::sync::Arc::clone(&group_outcomes);
            let completed = std::sync::Arc::clone(&completed_counter);
            let audit_dir = audit_dir.clone();
            let cargo_bin = cargo_bin.clone();

            handles.push(std::thread::spawn(
                move || -> Result<(), Box<dyn Error + Send + Sync>> {
                    loop {
                        let item = {
                            let mut lock = queue.lock().unwrap();
                            lock.next()
                        };
                        let Some((group_id, indices)) = item else {
                            break;
                        };

                        let group_dir = audit_dir.join(&group_id);
                        let crate_dir = group_dir.join("crate");

                        let mut attempts = 0;
                        let (out_str, err_str, exit_code) = loop {
                            attempts += 1;
                            let test_out = Command::new(&cargo_bin)
                                .args(["test", "--offline", "--", "--color", "never"])
                                .current_dir(&crate_dir)
                                .output();

                            let (out, err, code) = match test_out {
                                Ok(o) => (
                                    String::from_utf8_lossy(&o.stdout).to_string(),
                                    String::from_utf8_lossy(&o.stderr).to_string(),
                                    o.status.code().unwrap_or(-1),
                                ),
                                Err(e) => (String::new(), e.to_string(), -1),
                            };

                            if code != 0
                                && attempts < 4
                                && (err.contains("Access is denied")
                                    || err.contains("os error 5")
                                    || err.contains("os error 32"))
                            {
                                std::thread::sleep(std::time::Duration::from_millis(
                                    150 * attempts,
                                ));
                                continue;
                            }
                            break (out, err, code);
                        };

                        let full_output = format!("{}\n{}", out_str, err_str);
                        fs::write(group_dir.join("rust-test.log"), &full_output)?;

                        let is_build_error = exit_code != 0
                            && (err_str.contains("error[E")
                                || err_str.contains("could not compile"));
                        if is_build_error {
                            let diag = err_str.trim().replace('\t', " ").replace('\n', " ");
                            let results = indices
                                .iter()
                                .map(|&idx| EvaluationResult {
                                    indices: vec![idx],
                                    category: "BUILD_ERROR".to_string(),
                                    outcome: "BUILD_ERROR".to_string(),
                                    diagnostic: diag.clone(),
                                })
                                .collect();
                            let done =
                                completed.fetch_add(1, std::sync::atomic::Ordering::Relaxed) + 1;
                            println!(
                                "[{}/{}] Evaluated group {} ({} cases): BUILD_ERROR",
                                done,
                                total_ready,
                                group_id,
                                indices.len()
                            );
                            group_outcomes
                                .lock()
                                .unwrap()
                                .push(GroupOutcome { results });
                            continue;
                        }

                        let mut passed = 0;
                        let mut failed = 0;
                        let mut results = Vec::new();

                        for &idx in &indices {
                            let (category, outcome) = case_result(&full_output, idx);
                            let (cat, out, diag) = if category == "PASS" {
                                passed += 1;
                                ("PASS".to_string(), outcome.to_string(), String::new())
                            } else if category == "EXECUTION_ERROR" {
                                failed += 1;
                                (
                                    "EXECUTION_ERROR".to_string(),
                                    outcome.to_string(),
                                    format!(
                                        "{outcome}; process exit {exit_code}; see rust-test.log"
                                    ),
                                )
                            } else {
                                failed += 1;
                                (
                                    "FAILURE".to_string(),
                                    "FAILURE".to_string(),
                                    "Expected result differs".to_string(),
                                )
                            };
                            results.push(EvaluationResult {
                                indices: vec![idx],
                                category: cat,
                                outcome: out,
                                diagnostic: diag,
                            });
                        }

                        let done = completed.fetch_add(1, std::sync::atomic::Ordering::Relaxed) + 1;
                        let summary = if failed == 0 {
                            format!("PASS ({} cases)", passed)
                        } else {
                            format!("COMPLETED (passed={}, failed={})", passed, failed)
                        };
                        println!(
                            "[{}/{}] Evaluated group {} ({} cases): {}",
                            done,
                            total_ready,
                            group_id,
                            indices.len(),
                            summary
                        );
                        group_outcomes
                            .lock()
                            .unwrap()
                            .push(GroupOutcome { results });
                    }
                    Ok(())
                },
            ));
        }

        for handle in handles {
            handle.join().unwrap().map_err(|e| e.to_string())?;
        }
    }

    // Apply results to cases
    let outcomes = group_outcomes.lock().unwrap();
    for outcome in outcomes.iter() {
        for res in &outcome.results {
            for &idx in &res.indices {
                cases[idx].category = res.category.clone();
                cases[idx].outcome = res.outcome.clone();
                cases[idx].diagnostic = res.diagnostic.clone();
            }
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
    fn numeric_comparison_is_exact() {
        let source = format!(
            r#"
#[derive(PartialEq)]
enum Value {{
    Number {{ coefficient: String, scale: i32 }},
    List(Vec<Value>),
    Record(std::collections::BTreeMap<String, Value>),
}}
{}
fn main() {{
    let n = |c: &str, scale| Value::Number {{ coefficient: c.into(), scale }};
    assert!(equivalent(&n("100", 2), &n("1", 0)));
    assert!(equivalent(&n("1", -1), &n("10", 0)));
    assert!(!equivalent(&n("14", 1), &n("1", 0)));
    assert!(!equivalent(&n("1000000001", 9), &n("1", 0)));
    assert!(!equivalent(&Value::List(vec![n("14", 1)]), &Value::List(vec![n("1", 0)])));
    assert!(!equivalent(&Value::Record(std::collections::BTreeMap::from([("n".into(), n("14", 1))])),
        &Value::Record(std::collections::BTreeMap::from([("n".into(), n("1", 0))]))));
}}
"#,
            super::HARNESS_PRELUDE.replace("use generated_dmn::{Value, evaluate};", "")
        );
        let dir =
            std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("target/comparison-regression");
        std::fs::create_dir_all(&dir).unwrap();
        let source_path = dir.join("main.rs");
        let binary = dir.join(if cfg!(windows) {
            "compare.exe"
        } else {
            "compare"
        });
        std::fs::write(&source_path, source).unwrap();
        assert!(
            std::process::Command::new("rustc")
                .arg(&source_path)
                .arg("-o")
                .arg(&binary)
                .status()
                .unwrap()
                .success()
        );
        assert!(
            std::process::Command::new(binary)
                .status()
                .unwrap()
                .success()
        );
    }

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
