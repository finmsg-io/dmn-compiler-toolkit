//! Strict preflight for the admitted scalar shapes. Prost discards unknown fields.

pub const MAX_BYTES: usize = 4 * 1024 * 1024;
const MAX_DEPTH: usize = 32;
const MAX_FIELDS: usize = 200_000;
const MAX_MESSAGES: usize = 50_000;
const MAX_STRING: usize = 16_384;
const MAX_MODELS: usize = 128;
const MAX_DECLARATIONS: usize = 10_000;
const MAX_REFERENCES: usize = 50_000;

#[derive(Clone, Copy)]
enum Message {
    Artifact,
    Version,
    Model,
    Identity,
    Import,
    Input,
    Decision,
    Variable,
    Expression,
    Unary,
    Binary,
    Conditional,
    Record,
    RecordField,
    TypeReference,
    FieldAccess,
    LocalReference,
    Context,
    LocalBinding,
    Literal,
    Decimal,
    Date,
    Reference,
}

#[derive(Clone, Copy)]
enum Field {
    Text,
    Integer,
    Boolean,
    Nested(Message),
}

fn field(message: Message, tag: u32) -> Option<(Field, bool, bool)> {
    use Field::*;
    use Message::*;
    // Each rule specifies wire type, repetition, and membership in the message's oneof.
    let rule = match (message, tag) {
        (Artifact, 1) => (Nested(Version), false, false),
        (Artifact, 2 | 4) => (Text, false, false),
        (Artifact, 3) => (Text, true, false),
        (Artifact, 5) => (Nested(Model), true, false),
        (Artifact, 6) | (Decision, 3) => (Nested(Reference), true, false),
        (Version, 1 | 2) | (Variable, 3) | (Expression, 1) | (Decimal, 2) => {
            (Integer, false, false)
        }
        (Model | Input | Decision, 1) => (Nested(Identity), false, false),
        (Model, 2) | (Identity | Reference | Variable, 1 | 2) | (Import, 1..=3) | (Decimal, 1) => {
            (Text, false, false)
        }
        (Model, 3) => (Nested(Import), true, false),
        (Model, 4) => (Nested(Input), true, false),
        (Model, 5) => (Nested(Decision), true, false),
        (Model, 6) => (Nested(Record), true, false),
        (Variable, 4) | (Expression, 7) => (Nested(Reference), false, false),
        (Record, 1) => (Nested(Identity), false, false),
        (Record, 2) => (Nested(RecordField), true, false),
        (RecordField, 1 | 2) | (FieldAccess, 2) => (Text, false, false),
        (RecordField, 3) => (Nested(TypeReference), false, false),
        (TypeReference, 1) => (Integer, false, true),
        (TypeReference, 2) => (Nested(Reference), false, true),
        (Expression, 9) => (Nested(LocalReference), false, true),
        (Expression, 10) => (Nested(Context), false, true),
        (LocalReference | LocalBinding, 1) => (Text, false, false),
        (Context, 1) => (Nested(LocalBinding), true, false),
        (Context, 2) | (LocalBinding, 3) => (Nested(Expression), false, false),
        (LocalBinding, 2) => (Nested(Variable), false, false),
        (Expression, 8) => (Nested(FieldAccess), false, true),
        (FieldAccess, 1) => (Nested(Expression), false, false),
        (Input | Decision, 2) => (Nested(Variable), false, false),
        (Decision, 4) => (Nested(Expression), false, false),
        (Expression, 2) => (Nested(Literal), false, true),
        (Expression, 3) => (Nested(Reference), false, true),
        (Expression, 4) => (Nested(Unary), false, true),
        (Expression, 5) => (Nested(Binary), false, true),
        (Expression, 6) => (Nested(Conditional), false, true),
        (Unary | Binary, 1) => (Integer, false, false),
        (Unary, 2) | (Binary, 2 | 3) | (Conditional, 1..=3) => (Nested(Expression), false, false),
        (Literal, 1) => (Integer, false, true),
        (Literal, 2) => (Boolean, false, true),
        (Literal, 3) => (Text, false, true),
        (Literal, 5) => (Nested(Date), false, true),
        (Date, 1..=3) => (Integer, false, false),
        (Literal, 4) => (Nested(Decimal), false, true),
        _ => return None,
    };
    Some(rule)
}

#[derive(Default)]
struct Budget {
    fields: usize,
    messages: usize,
    models: usize,
    declarations: usize,
    references: usize,
}

pub fn verify(bytes: &[u8]) -> Result<(), &'static str> {
    if bytes.len() > MAX_BYTES {
        return Err("artifact exceeds byte budget");
    }
    message(bytes, Message::Artifact, 1, &mut Budget::default())
}

fn message(
    mut bytes: &[u8],
    kind: Message,
    depth: usize,
    budget: &mut Budget,
) -> Result<(), &'static str> {
    budget.messages += 1;
    match kind {
        Message::Model => budget.models += 1,
        Message::Input
        | Message::Decision
        | Message::Record
        | Message::RecordField
        | Message::LocalBinding => budget.declarations += 1,
        Message::Reference | Message::LocalReference => budget.references += 1,
        _ => {}
    }
    if depth > MAX_DEPTH
        || budget.messages > MAX_MESSAGES
        || budget.models > MAX_MODELS
        || budget.declarations > MAX_DECLARATIONS
        || budget.references > MAX_REFERENCES
    {
        return Err("artifact exceeds message budget");
    }
    let mut seen = 0u32;
    let mut oneof = false;
    while !bytes.is_empty() {
        budget.fields += 1;
        if budget.fields > MAX_FIELDS {
            return Err("artifact exceeds field budget");
        }
        let key = u32::try_from(varint(&mut bytes)?).map_err(|_| "invalid field tag")?;
        let tag = key >> 3;
        let (field, repeated, choice) = field(kind, tag).ok_or("field outside scalar profile")?;
        let bit = 1 << tag;
        if (!repeated && seen & bit != 0) || (choice && oneof) {
            return Err("duplicate field or oneof choice");
        }
        seen |= bit;
        oneof |= choice;
        match (field, key & 7) {
            (Field::Text, 2) => {
                let text = delimited(&mut bytes)?;
                if text.len() > MAX_STRING {
                    return Err("artifact exceeds string budget");
                }
                std::str::from_utf8(text).map_err(|_| "invalid UTF-8")?;
            }
            (Field::Nested(child), 2) => {
                message(delimited(&mut bytes)?, child, depth + 1, budget)?;
            }
            (Field::Integer, 0) => {
                u32::try_from(varint(&mut bytes)?).map_err(|_| "scalar exceeds wire range")?;
            }
            (Field::Boolean, 0) => {
                if varint(&mut bytes)? > 1 {
                    return Err("invalid boolean encoding");
                }
            }
            _ => return Err("wrong wire type"),
        }
    }
    Ok(())
}

fn delimited<'a>(bytes: &mut &'a [u8]) -> Result<&'a [u8], &'static str> {
    let len = usize::try_from(varint(bytes)?).map_err(|_| "invalid field length")?;
    let (value, rest) = bytes.split_at_checked(len).ok_or("truncated field")?;
    *bytes = rest;
    Ok(value)
}

fn varint(bytes: &mut &[u8]) -> Result<u64, &'static str> {
    let mut value = 0;
    for shift in (0..64).step_by(7) {
        let (&byte, rest) = bytes.split_first().ok_or("truncated varint")?;
        *bytes = rest;
        if shift == 63 && byte > 1 {
            return Err("varint exceeds 64 bits");
        }
        value |= u64::from(byte & 0x7f) << shift;
        if byte < 0x80 {
            return Ok(value);
        }
    }
    Err("invalid varint")
}
