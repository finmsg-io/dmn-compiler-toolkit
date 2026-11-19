use std::env;
use std::fs;
use std::path::{Path, PathBuf};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let out_dir = PathBuf::from(env::var("OUT_DIR")?);
    let target_file = out_dir.join("io.finmsg.dmn.dmnir.v0.rs");

    let schema_dir = Path::new("../dmn-ir-protobuf/src/main/proto");
    let schema = schema_dir.join("dmnir/v0/dmnir.proto");
    println!("cargo:rerun-if-changed={}", schema.display());

    let mut config = prost_build::Config::new();
    let res = (|| -> Result<(), Box<dyn std::error::Error>> {
        if let Ok(protoc) = env::var("PROTOC") {
            config.protoc_executable(PathBuf::from(protoc));
        } else {
            let m2_protoc = PathBuf::from(
                "C:/Users/lik/.m2/repository/com/google/protobuf/protoc/4.31.1/protoc-4.31.1-windows-x86_64.exe",
            );
            if m2_protoc.exists() {
                config.protoc_executable(m2_protoc);
            } else if let Ok(path) = protoc_bin_vendored::protoc_bin_path() {
                config.protoc_executable(path);
            }
        }
        config.compile_protos(&[schema], &[schema_dir.to_path_buf()])?;
        Ok(())
    })();

    if let Err(ref e) = res {
        eprintln!("compile_protos failed: {e:?}");
        if let Ok(entries) = fs::read_dir("target/debug/build") {
            for entry in entries.flatten() {
                let candidate = entry.path().join("out/io.finmsg.dmn.dmnir.v0.rs");
                if candidate.exists() && candidate != target_file {
                    match fs::copy(&candidate, &target_file) {
                        Ok(_) => {
                            eprintln!("Copied cached schema from {}", candidate.display());
                            return Ok(());
                        }
                        Err(copy_err) => {
                            eprintln!("Failed to copy {}: {copy_err:?}", candidate.display());
                        }
                    }
                }
            }
        }
        res?;
    }
    Ok(())
}
