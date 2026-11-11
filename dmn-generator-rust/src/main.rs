mod bounds;
mod date;
mod decimal;
mod model;
mod types;
mod wire;

mod proto {
    include!(concat!(env!("OUT_DIR"), "/io.finmsg.dmn.dmnir.v0.rs"));
}

use prost::Message;
use std::error::Error;
use std::fs::{self, File};
use std::io::Read;
use std::path::Path;

fn main() -> Result<(), Box<dyn Error>> {
    let arguments: Vec<_> = std::env::args_os().skip(1).collect();
    let [artifact, output] = arguments.as_slice() else {
        return Err("usage: dmn-generator-rust <artifact.dmnir> <new-output-directory>".into());
    };
    let mut bytes = Vec::new();
    File::open(artifact)?
        .take((wire::MAX_BYTES + 1) as u64)
        .read_to_end(&mut bytes)?;
    wire::verify(&bytes)?;
    let artifact = proto::DmnIrArtifact::decode(bytes.as_slice())?;
    let source = model::verify_and_generate(&artifact)?;

    let output = Path::new(output);
    fs::create_dir(output)?;
    fs::write(output.join("lib.rs"), source)?;
    fs::write(
        output.join("Cargo.toml"),
        "[package]\nname = \"generated-dmn\"\nversion = \"0.1.0\"\nedition = \"2024\"\npublish = false\n\n[lib]\npath = \"lib.rs\"\n",
    )?;
    Ok(())
}
