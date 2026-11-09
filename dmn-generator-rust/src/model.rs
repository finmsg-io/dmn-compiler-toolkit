use crate::decimal;
use crate::proto::*;
use dmn_ir_expression::Node;
use dmn_ir_literal::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write;

type Key<'a> = (&'a str, &'a str);
type Check<T> = Result<T, &'static str>;

#[cfg(test)]
mod tests {
    use super::*;
    use prost::Message;

    #[test]
    fn checks_schedule_and_declared_references() {
        let bytes = include_bytes!("../tests/fixtures/scalar-0.0.dmnir");
        let valid = DmnIrArtifact::decode(bytes.as_slice()).unwrap();
        let mut reversed = valid.clone();
        reversed.evaluation_order.reverse();
        assert_eq!(
            verify_and_generate(&reversed),
            Err("invalid decision dependency or evaluation order")
        );

        let mut undeclared = valid;
        let imported = undeclared
            .models
            .iter_mut()
            .find(|model| model.namespace == "urn:rust-proof:root")
            .unwrap()
            .decisions
            .iter_mut()
            .find(|decision| decision.identity.as_ref().unwrap().id == "rate")
            .unwrap();
        imported.dependencies.clear();
        assert_eq!(
            verify_and_generate(&undeclared),
            Err("expression reference is not a declared dependency")
        );
    }
}

struct Declaration<'a> {
    ty: DmnIrScalarType,
    decision: Option<&'a DmnIrDecision>,
}

pub fn verify_and_generate(artifact: &DmnIrArtifact) -> Check<String> {
    let version = artifact
        .schema_version
        .as_ref()
        .ok_or("missing schema version")?;
    if version.major != Some(0)
        || version.minor != Some(0)
        || artifact.semantics_version != "finmsg-dmnir-scalar/0"
        || artifact.required_capabilities != ["dmnir.scalar"]
    {
        return Err("unsupported DMNIR profile");
    }
    let mut models = BTreeMap::new();
    let mut declarations = BTreeMap::new();
    for model in &artifact.models {
        let ns = nonempty(&model.namespace)?;
        if models.insert(ns, model).is_some() {
            return Err("duplicate model namespace");
        }
        let mut ids = BTreeSet::new();
        let mut names = BTreeSet::new();
        let mut variable_names = BTreeSet::new();
        let identity = model.identity.as_ref().ok_or("missing model identity")?;
        identity_fields(identity, &mut ids)?;
        let inputs = model
            .inputs
            .iter()
            .map(|input| (&input.identity, &input.variable, None));
        let decisions = model
            .decisions
            .iter()
            .map(|decision| (&decision.identity, &decision.variable, Some(decision)));
        for (identity, variable, decision) in inputs.chain(decisions) {
            let identity = identity.as_ref().ok_or("missing element identity")?;
            let variable = variable.as_ref().ok_or("missing variable")?;
            identity_fields(identity, &mut ids)?;
            if !names.insert(&identity.name) || !variable_names.insert(nonempty(&variable.name)?) {
                return Err("duplicate declaration name");
            }
            if let Some(id) = &variable.id
                && !ids.insert(nonempty(id)?)
            {
                return Err("duplicate variable ID");
            }
            declarations.insert(
                (ns, identity.id.as_str()),
                Declaration {
                    ty: scalar(variable.r#type)?,
                    decision,
                },
            );
        }
    }
    if !models.contains_key(artifact.root_model_namespace.as_str()) {
        return Err("root model is absent");
    }
    let mut imports = BTreeMap::new();
    for (&ns, model) in &models {
        let mut aliases = BTreeSet::new();
        let mut direct = BTreeSet::new();
        for import in &model.imports {
            let target = import.model_namespace.as_str();
            if target == ns
                || !models.contains_key(target)
                || !aliases.insert(nonempty(&import.name)?)
            {
                return Err("invalid import");
            }
            direct.insert(target);
        }
        imports.insert(ns, direct);
    }
    let slots: BTreeMap<_, _> = declarations
        .keys()
        .copied()
        .enumerate()
        .map(|(slot, key)| (key, slot))
        .collect();
    let mut source = String::from(include_str!("decimal.rs"));
    source.push('\n');
    source.push_str(include_str!("generated_prelude.rs.txt"));
    let mut ready = BTreeSet::new();
    for (&key, declaration) in &declarations {
        if declaration.decision.is_none() {
            ready.insert(key);
            writeln!(
                source,
                "    let v{} = bind(inputs.get(&{key:?}).ok_or(\"missing input\")?, {})?;",
                slots[&key], declaration.ty as i32
            )
            .unwrap();
        }
    }
    writeln!(
        source,
        "    if inputs.len() != {} {{ return Err(\"unexpected input\"); }}",
        ready.len()
    )
    .unwrap();
    source.push_str("    let mut results = BTreeMap::new();\n");
    for reference in &artifact.evaluation_order {
        let key = key(reference);
        let declaration = declarations.get(&key).ok_or("unresolved schedule entry")?;
        let decision = declaration.decision.ok_or("schedule contains an input")?;
        if !ready.insert(key) {
            return Err("duplicate schedule entry");
        }
        let mut dependencies = BTreeSet::new();
        for dependency in &decision.dependencies {
            let dependency = self::key(dependency);
            if dependency == key || !ready.contains(&dependency) || !dependencies.insert(dependency)
            {
                return Err("invalid decision dependency or evaluation order");
            }
            if dependency.0 != key.0 && !imports[key.0].contains(dependency.0) {
                return Err("dependency requires a direct import");
            }
        }
        let expression = decision
            .expression
            .as_ref()
            .ok_or("missing decision expression")?;
        let ty = scalar(expression.r#type)?;
        if !assignable(ty, declaration.ty) {
            return Err("decision expression type mismatch");
        }
        let value = match expression.node.as_ref().ok_or("missing expression node")? {
            Node::Literal(literal) => literal_source(literal, ty)?,
            Node::GlobalReference(reference) => {
                let target = self::key(reference);
                if !dependencies.contains(&target) {
                    return Err("expression reference is not a declared dependency");
                }
                if declarations[&target].ty != ty {
                    return Err("reference type mismatch");
                }
                format!("v{}.clone()", slots[&target])
            }
            _ => return Err("expression outside scalar profile"),
        };
        writeln!(source, "    let v{} = {value};", slots[&key]).unwrap();
        writeln!(
            source,
            "    results.insert({key:?}, v{}.clone());",
            slots[&key]
        )
        .unwrap();
    }
    if ready.len() != declarations.len() {
        return Err("evaluation order omits a decision");
    }
    source.push_str("    Ok(results)\n}\n");
    Ok(source)
}

fn key(reference: &DmnIrReference) -> Key<'_> {
    (&reference.model_namespace, &reference.element_id)
}

fn identity_fields<'a>(
    identity: &'a DmnIrElementIdentity,
    ids: &mut BTreeSet<&'a str>,
) -> Check<()> {
    nonempty(&identity.name)?;
    if !ids.insert(nonempty(&identity.id)?) {
        return Err("duplicate element ID");
    }
    Ok(())
}

fn nonempty(value: &str) -> Check<&str> {
    if value.trim().is_empty() {
        Err("blank declaration or reference")
    } else {
        Ok(value)
    }
}

fn scalar(value: i32) -> Check<DmnIrScalarType> {
    use DmnIrScalarType::*;
    match DmnIrScalarType::try_from(value) {
        Ok(value @ (ScalarTypeNull | ScalarTypeBoolean | ScalarTypeNumber | ScalarTypeString)) => {
            Ok(value)
        }
        _ => Err("type outside scalar profile"),
    }
}

fn assignable(actual: DmnIrScalarType, expected: DmnIrScalarType) -> bool {
    actual == expected || actual == DmnIrScalarType::ScalarTypeNull
}

fn literal_source(literal: &DmnIrLiteral, expected: DmnIrScalarType) -> Check<String> {
    use DmnIrScalarType::*;
    let (actual, source) = match literal.value.as_ref().ok_or("missing literal value")? {
        Value::NullValue(0) => (ScalarTypeNull, "Value::Null".into()),
        Value::BooleanValue(value) => (ScalarTypeBoolean, format!("Value::Boolean({value})")),
        Value::StringValue(value) => (ScalarTypeString, format!("Value::Text({value:?}.into())")),
        Value::NumberValue(value) => {
            let scale = value.scale.ok_or("missing decimal scale")?;
            let coefficient = &value.unscaled_value;
            if !decimal::valid_decimal(coefficient, scale) {
                return Err("invalid decimal");
            }
            (
                ScalarTypeNumber,
                format!("Value::Number {{ coefficient: {coefficient:?}.into(), scale: {scale} }}"),
            )
        }
        _ => return Err("literal outside scalar profile"),
    };
    if !assignable(actual, expected) {
        return Err("literal type mismatch");
    }
    Ok(source)
}
