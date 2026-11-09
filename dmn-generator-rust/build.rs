use std::path::Path;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let schema_dir = Path::new("../dmn-ir-protobuf/src/main/proto");
    let schema = schema_dir.join("dmnir/v0/dmnir.proto");
    println!("cargo:rerun-if-changed={}", schema.display());
    prost_build::Config::new()
        .protoc_executable(protoc_bin_vendored::protoc_bin_path()?)
        .compile_protos(&[schema], &[schema_dir.to_path_buf()])?;
    Ok(())
}
