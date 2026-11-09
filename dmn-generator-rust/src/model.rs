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

    #[test]
    fn requires_expression_profile_and_checks_unselected_branches() {
        let mut artifact = expressions();
        artifact.schema_version.as_mut().unwrap().minor = Some(0);
        artifact.semantics_version = "finmsg-dmnir-scalar/0".into();
        artifact.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&artifact),
            Err("expression outside scalar profile")
        );

        let mut artifact = expressions();
        let conditional = selected(&mut artifact);
        conditional.condition = Some(Box::new(DmnIrExpression {
            r#type: DmnIrScalarType::ScalarTypeBoolean as i32,
            node: Some(Node::Literal(DmnIrLiteral {
                value: Some(Value::BooleanValue(true)),
            })),
            ..Default::default()
        }));
        let original = conditional.else_expression.clone();
        conditional.else_expression = Some(Box::new(DmnIrExpression {
            r#type: DmnIrScalarType::ScalarTypeNumber as i32,
            node: Some(Node::Binary(Box::new(DmnIrBinaryExpression {
                operator: DmnIrBinaryOperator::BinaryOperatorAdd as i32,
                left: original.clone(),
                right: original,
            }))),
            ..Default::default()
        }));
        assert_eq!(
            verify_and_generate(&artifact),
            Err("unsupported binary signature")
        );

        let conditional = selected(&mut artifact);
        conditional.else_expression = None;
        assert_eq!(
            verify_and_generate(&artifact),
            Err("missing else expression")
        );
    }

    #[test]
    fn preflight_bounds_recursive_expression_depth() {
        let mut artifact = expressions();
        let conditional = selected(&mut artifact);
        let mut operand = conditional.else_expression.take().unwrap();
        for _ in 0..32 {
            operand = Box::new(DmnIrExpression {
                r#type: DmnIrScalarType::ScalarTypeNumber as i32,
                node: Some(Node::Unary(Box::new(DmnIrUnaryExpression {
                    operator: DmnIrUnaryOperator::UnaryOperatorPositive as i32,
                    operand: Some(operand),
                }))),
                ..Default::default()
            });
        }
        conditional.else_expression = Some(operand);
        assert_eq!(
            crate::wire::verify(&artifact.encode_to_vec()),
            Err("artifact exceeds message budget")
        );
    }

    fn expressions() -> DmnIrArtifact {
        let bytes = include_bytes!("../tests/fixtures/comparisons-0.1.dmnir");
        DmnIrArtifact::decode(bytes.as_slice()).unwrap()
    }

    fn selected(artifact: &mut DmnIrArtifact) -> &mut DmnIrConditionalExpression {
        let decision = artifact.models[0]
            .decisions
            .iter_mut()
            .find(|decision| decision.identity.as_ref().unwrap().id == "selected")
            .unwrap();
        let Some(Node::Conditional(conditional)) = &mut decision.expression.as_mut().unwrap().node
        else {
            panic!("expected fixture conditional");
        };
        conditional
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
    let structured = match (
        version.major,
        version.minor,
        artifact.semantics_version.as_str(),
    ) {
        (Some(0), Some(0), "finmsg-dmnir-scalar/0")
            if artifact.required_capabilities == ["dmnir.scalar"] =>
        {
            false
        }
        (Some(0), Some(1), "finmsg-dmnir-scalar-expressions/0")
            if artifact.required_capabilities == ["dmnir.scalar", "dmnir.scalar-expressions"] =>
        {
            true
        }
        _ => return Err("unsupported DMNIR profile"),
    };
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
    if structured {
        source.push_str(include_str!("generated_expressions.rs.txt"));
    }
    source.push_str("pub fn evaluate(inputs: &BTreeMap<Key<'_>, Value>) -> Result<BTreeMap<Key<'static>, Value>, &'static str> {\n");
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
        let emitter = ExpressionEmitter {
            structured,
            dependencies: &dependencies,
            declarations: &declarations,
            slots: &slots,
        };
        let (ty, value) = emitter.expression(expression)?;
        if !assignable(ty, declaration.ty) {
            return Err("decision expression type mismatch");
        }
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

struct ExpressionEmitter<'a, 'model> {
    structured: bool,
    dependencies: &'a BTreeSet<Key<'model>>,
    declarations: &'a BTreeMap<Key<'model>, Declaration<'model>>,
    slots: &'a BTreeMap<Key<'model>, usize>,
}

impl ExpressionEmitter<'_, '_> {
    fn expression(&self, expression: &DmnIrExpression) -> Check<(DmnIrScalarType, String)> {
        use DmnIrScalarType::{
            ScalarTypeBoolean as Boolean, ScalarTypeNumber as Number, ScalarTypeString as Text,
        };
        use DmnIrUnaryOperator::{UnaryOperatorNegate, UnaryOperatorNot, UnaryOperatorPositive};

        let ty = scalar(expression.r#type)?;
        let value = match expression.node.as_ref().ok_or("missing expression node")? {
            Node::Literal(literal) => literal_source(literal, ty)?,
            Node::GlobalReference(reference) => {
                let target = key(reference);
                if !self.dependencies.contains(&target) {
                    return Err("expression reference is not a declared dependency");
                }
                if !assignable(self.declarations[&target].ty, ty) {
                    return Err("reference type mismatch");
                }
                format!("v{}.clone()", self.slots[&target])
            }
            Node::Unary(unary) if self.structured => {
                let (operand_type, operand) =
                    self.expression(unary.operand.as_deref().ok_or("missing unary operand")?)?;
                match DmnIrUnaryOperator::try_from(unary.operator) {
                    Ok(UnaryOperatorPositive) if ty == Number && operand_type == Number => operand,
                    Ok(UnaryOperatorNegate) if ty == Number && operand_type == Number => {
                        format!("negate({operand})")
                    }
                    Ok(UnaryOperatorNot) if ty == Boolean && assignable(operand_type, Boolean) => {
                        format!("not({operand})")
                    }
                    _ => return Err("unsupported unary signature"),
                }
            }
            Node::Binary(binary) if self.structured => {
                use DmnIrBinaryOperator::*;
                let (left_type, left) =
                    self.expression(binary.left.as_deref().ok_or("missing left operand")?)?;
                let (right_type, right) =
                    self.expression(binary.right.as_deref().ok_or("missing right operand")?)?;
                if ty != Boolean {
                    return Err("unsupported binary signature");
                }
                let logical = assignable(left_type, Boolean) && assignable(right_type, Boolean);
                let ordered = left_type == right_type && matches!(left_type, Number | Text);
                match DmnIrBinaryOperator::try_from(binary.operator) {
                    Ok(BinaryOperatorEqual) => format!("equal({left}, {right})"),
                    Ok(BinaryOperatorNotEqual) => format!("not(equal({left}, {right}))"),
                    Ok(BinaryOperatorAnd) if logical => format!("and({left}, || {right})"),
                    Ok(BinaryOperatorOr) if logical => format!("or({left}, || {right})"),
                    Ok(BinaryOperatorLess) if ordered => {
                        format!("ordered({left}, {right}, Ordering::is_lt)")
                    }
                    Ok(BinaryOperatorLessEqual) if ordered => {
                        format!("ordered({left}, {right}, Ordering::is_le)")
                    }
                    Ok(BinaryOperatorGreater) if ordered => {
                        format!("ordered({left}, {right}, Ordering::is_gt)")
                    }
                    Ok(BinaryOperatorGreaterEqual) if ordered => {
                        format!("ordered({left}, {right}, Ordering::is_ge)")
                    }
                    _ => return Err("unsupported binary signature"),
                }
            }
            Node::Conditional(conditional) if self.structured => {
                let (condition_type, condition) = self.expression(
                    conditional
                        .condition
                        .as_deref()
                        .ok_or("missing condition")?,
                )?;
                let (then_type, then_value) = self.expression(
                    conditional
                        .then_expression
                        .as_deref()
                        .ok_or("missing then expression")?,
                )?;
                let (else_type, else_value) = self.expression(
                    conditional
                        .else_expression
                        .as_deref()
                        .ok_or("missing else expression")?,
                )?;
                if !assignable(condition_type, Boolean)
                    || !assignable(then_type, ty)
                    || !assignable(else_type, ty)
                {
                    return Err("conditional type mismatch");
                }
                format!(
                    "if matches!({condition}, Value::Boolean(true)) {{ {then_value} }} else {{ {else_value} }}"
                )
            }
            _ => return Err("expression outside scalar profile"),
        };
        Ok((ty, value))
    }
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
