#[path = "constraints.rs"]
mod constraints;
#[path = "tables.rs"]
mod tables;
use crate::bounds::{MAX_DIVISION_LIMB_VISITS, MAX_LIMB_PRODUCTS, NumericBounds};
use crate::decimal;
use crate::proto::*;
use crate::types::{Type, Types};
use dmn_ir_expression::Node;
use dmn_ir_literal::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write;

type Key<'a> = (&'a str, &'a str);
type Check<T> = Result<T, &'static str>;
const MAX_ARITHMETIC_CHAIN: usize = 128;

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
                operator: DmnIrBinaryOperator::BinaryOperatorPower as i32,
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

    #[test]
    fn bounds_arithmetic_across_decisions() {
        let mut artifact = expressions();
        artifact.evaluation_order.clear();
        let model = &mut artifact.models[0];
        model.decisions.clear();
        let mut previous = DmnIrReference {
            model_namespace: model.namespace.clone(),
            element_id: "a".into(),
        };
        let number = DmnIrScalarType::ScalarTypeNumber as i32;
        for index in 1..=MAX_ARITHMETIC_CHAIN + 1 {
            let id = format!("sum-{index}");
            let operand = Box::new(DmnIrExpression {
                r#type: number,
                node: Some(Node::GlobalReference(previous.clone())),
                ..Default::default()
            });
            model.decisions.push(DmnIrDecision {
                identity: Some(DmnIrElementIdentity {
                    id: id.clone(),
                    name: id.clone(),
                }),
                variable: Some(DmnIrVariable {
                    name: id.clone(),
                    r#type: number,
                    ..Default::default()
                }),
                dependencies: vec![previous.clone()],
                expression: Some(DmnIrExpression {
                    r#type: number,
                    node: Some(Node::Binary(Box::new(DmnIrBinaryExpression {
                        operator: DmnIrBinaryOperator::BinaryOperatorAdd as i32,
                        left: Some(operand.clone()),
                        right: Some(operand),
                    }))),
                    ..Default::default()
                }),
                ..Default::default()
            });
            previous.element_id = id;
            artifact.evaluation_order.push(previous.clone());
        }
        assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal arithmetic chain exceeds 128 operations")
        );
        artifact.models[0].decisions.pop();
        artifact.evaluation_order.pop();
        assert!(verify_and_generate(&artifact).is_ok());
    }

    #[test]
    fn admits_the_previous_arithmetic_rejection_fixture() {
        for bytes in [
            include_bytes!("../tests/fixtures/expressions-0.1.dmnir").as_slice(),
            include_bytes!("../tests/fixtures/unsupported-multiplication-0.1.dmnir").as_slice(),
            include_bytes!("../tests/fixtures/unsupported-division-0.1.dmnir").as_slice(),
        ] {
            crate::wire::verify(bytes).unwrap();
            let artifact = DmnIrArtifact::decode(bytes).unwrap();
            assert!(verify_and_generate(&artifact).is_ok());
        }
    }

    #[test]
    fn bounds_product_scale_in_an_unselected_branch() {
        let mut artifact = expressions();
        let conditional = selected(&mut artifact);
        let a = conditional
            .then_expression
            .as_ref()
            .unwrap()
            .as_ref()
            .clone();
        let b = conditional
            .else_expression
            .as_ref()
            .unwrap()
            .as_ref()
            .clone();
        conditional.condition = Some(Box::new(DmnIrExpression {
            r#type: DmnIrScalarType::ScalarTypeBoolean as i32,
            node: Some(Node::Literal(DmnIrLiteral {
                value: Some(Value::BooleanValue(true)),
            })),
            ..Default::default()
        }));
        let product = multiply(a.clone(), b);
        conditional.else_expression = Some(Box::new(product.clone()));
        assert!(verify_and_generate(&artifact).is_ok());
        selected(&mut artifact).else_expression = Some(Box::new(multiply(product, a)));
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal scale bound exceeds 8192")
        );
    }

    #[test]
    fn bounds_product_coefficient_digits() {
        let mut artifact = expressions();
        let mut expression = decimal_literal("9".repeat(4096));
        for _ in 0..3 {
            expression = multiply(expression.clone(), expression);
        }
        selected(&mut artifact).then_expression = Some(Box::new(expression.clone()));
        assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
        assert!(verify_and_generate(&artifact).is_ok());
        selected(&mut artifact).then_expression =
            Some(Box::new(multiply(expression, decimal_literal("9".into()))));
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal coefficient bound exceeds 32768 digits")
        );
    }

    #[test]
    fn bounds_total_multiplication_work_across_decisions() {
        let bytes = include_bytes!("../tests/fixtures/multiplication-0.1.dmnir");
        let mut artifact = repeated_decisions(bytes, "product", 41);
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal multiplication work bound exceeds 8388608 limb products")
        );
        artifact.models[0].decisions.pop();
        artifact.evaluation_order.pop();
        assert!(verify_and_generate(&artifact).is_ok());
    }

    #[test]
    fn bounds_total_division_work_across_decisions() {
        let bytes = include_bytes!("../tests/fixtures/division-0.1.dmnir");
        let mut artifact = repeated_decisions(bytes, "quotient", 26);
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal division work bound exceeds 8388608 limb visits")
        );
        artifact.models[0].decisions.pop();
        artifact.evaluation_order.pop();
        assert!(verify_and_generate(&artifact).is_ok());
    }

    #[test]
    fn bounds_division_scale_in_unselected_branch() {
        let mut artifact = expressions();
        let conditional = selected(&mut artifact);
        let a = conditional
            .then_expression
            .as_ref()
            .unwrap()
            .as_ref()
            .clone();
        let b = conditional
            .else_expression
            .as_ref()
            .unwrap()
            .as_ref()
            .clone();
        let divide = |left, right| binary(DmnIrBinaryOperator::BinaryOperatorDivide, left, right);
        conditional.condition = Some(Box::new(DmnIrExpression {
            r#type: DmnIrScalarType::ScalarTypeBoolean as i32,
            node: Some(Node::Literal(DmnIrLiteral {
                value: Some(Value::BooleanValue(true)),
            })),
            ..Default::default()
        }));
        let quotient = divide(a, b);
        let second = divide(quotient.clone(), quotient);
        conditional.else_expression = Some(Box::new(second.clone()));
        assert!(verify_and_generate(&artifact).is_ok());
        selected(&mut artifact).else_expression = Some(Box::new(divide(second.clone(), second)));
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal division scale bound exceeds 32768")
        );
    }

    #[test]
    fn validates_local_scope_identity_and_types() {
        let valid = DmnIrArtifact::decode(
            include_bytes!("../tests/fixtures/contexts-0.3.dmnir").as_slice(),
        )
        .unwrap();
        let context = |a: &mut DmnIrArtifact, edit: fn(&mut DmnIrContextExpression)| {
            let Some(Node::Context(c)) = &mut a
                .models
                .iter_mut()
                .find(|m| !m.decisions.is_empty())
                .unwrap()
                .decisions[0]
                .expression
                .as_mut()
                .unwrap()
                .node
            else {
                panic!()
            };
            edit(c);
        };
        for (edit, expected) in [
            (
                (|c: &mut DmnIrContextExpression| {
                    c.result = None;
                }) as fn(&mut DmnIrContextExpression),
                "missing context result",
            ),
            (
                |c| {
                    c.bindings[1].binding_id = "first".into();
                },
                "duplicate local binding ID",
            ),
            (
                |c| {
                    c.bindings[1].variable.as_mut().unwrap().name = "Local".into();
                },
                "duplicate context variable name",
            ),
            (
                |c| {
                    c.bindings[0].value = c.bindings[1].value.clone();
                },
                "local binding is not visible",
            ),
            (
                |c| {
                    c.bindings[1].value.as_mut().unwrap().node =
                        Some(Node::LocalReference(DmnIrLocalReference {
                            binding_id: "shadow".into(),
                        }));
                },
                "local binding is not visible",
            ),
            (
                |c| {
                    let value = c.bindings[1].value.as_mut().unwrap();
                    value.record_type = None;
                    value.r#type = DmnIrScalarType::ScalarTypeBoolean as i32;
                },
                "local reference type mismatch",
            ),
            (
                |c| {
                    c.bindings[1].variable.as_mut().unwrap().id = Some("records".into());
                },
                "duplicate variable ID",
            ),
        ] {
            let mut artifact = valid.clone();
            context(&mut artifact, edit);
            assert_eq!(verify_and_generate(&artifact), Err(expected));
        }
        let mut old = valid;
        old.schema_version.as_mut().unwrap().minor = Some(2);
        old.semantics_version = "finmsg-dmnir-records/0".into();
        old.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&old),
            Err("expression outside scalar profile")
        );
    }

    #[test]
    fn local_references_preserve_arithmetic_limits() {
        let mut artifact = expressions();
        artifact.schema_version.as_mut().unwrap().minor = Some(3);
        artifact.semantics_version = "finmsg-dmnir-local-bindings/0".into();
        artifact
            .required_capabilities
            .extend(["dmnir.records".into(), "dmnir.local-bindings".into()]);
        let number = DmnIrScalarType::ScalarTypeNumber as i32;
        let mut context = DmnIrContextExpression::default();
        let local = |id: String| DmnIrExpression {
            r#type: number,
            node: Some(Node::LocalReference(DmnIrLocalReference { binding_id: id })),
            ..Default::default()
        };
        for index in 0..=MAX_ARITHMETIC_CHAIN + 1 {
            let value = if index == 0 {
                decimal_literal("1".into())
            } else {
                binary(
                    DmnIrBinaryOperator::BinaryOperatorAdd,
                    local(format!("local-{}", index - 1)),
                    decimal_literal("1".into()),
                )
            };
            context.bindings.push(DmnIrLocalBinding {
                binding_id: format!("local-{index}"),
                variable: Some(DmnIrVariable {
                    name: format!("Local{index}"),
                    r#type: number,
                    ..Default::default()
                }),
                value: Some(value),
                ..Default::default()
            });
        }
        context.result = Some(Box::new(local(format!(
            "local-{}",
            MAX_ARITHMETIC_CHAIN + 1
        ))));
        let expression = DmnIrExpression {
            r#type: number,
            node: Some(Node::Context(Box::new(context))),
            ..Default::default()
        };
        let decision = artifact.models[0]
            .decisions
            .iter_mut()
            .find(|d| d.identity.as_ref().unwrap().id == "selected")
            .unwrap();
        decision.expression = Some(expression);
        assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal arithmetic chain exceeds 128 operations")
        );
        let decision = artifact.models[0]
            .decisions
            .iter_mut()
            .find(|d| d.identity.as_ref().unwrap().id == "selected")
            .unwrap();
        let Some(Node::Context(c)) = &mut decision.expression.as_mut().unwrap().node else {
            panic!()
        };
        c.bindings.pop();
        c.result = Some(Box::new(local(format!("local-{MAX_ARITHMETIC_CHAIN}"))));
        assert!(verify_and_generate(&artifact).is_ok());
    }

    #[test]
    fn validates_calendar_literals_and_profile() {
        let valid =
            DmnIrArtifact::decode(include_bytes!("../tests/fixtures/dates-0.4.dmnir").as_slice())
                .unwrap();
        for (year, month, day, expected) in [
            (None, Some(1), Some(1), "missing date year"),
            (Some(2024), None, Some(1), "missing date month"),
            (Some(2024), Some(1), None, "missing date day"),
            (Some(0), Some(1), Some(1), "invalid civil date"),
            (Some(1900), Some(2), Some(29), "invalid civil date"),
            (Some(-100), Some(2), Some(29), "invalid civil date"),
            (Some(1_000_000_000), Some(1), Some(1), "invalid civil date"),
            (Some(2024), Some(13), Some(1), "invalid civil date"),
            (Some(2024), Some(1), Some(0), "invalid civil date"),
        ] {
            let mut artifact = valid.clone();
            let expression = artifact.models[0]
                .decisions
                .iter_mut()
                .find(|d| d.identity.as_ref().unwrap().id == "literal0")
                .unwrap()
                .expression
                .as_mut()
                .unwrap();
            expression.node = Some(Node::Literal(DmnIrLiteral {
                value: Some(Value::DateValue(DmnIrDate { year, month, day })),
            }));
            assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
            assert_eq!(verify_and_generate(&artifact), Err(expected));
        }
        let mut old = valid;
        old.schema_version.as_mut().unwrap().minor = Some(3);
        old.semantics_version = "finmsg-dmnir-local-bindings/0".into();
        old.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&old),
            Err("type outside scalar profile")
        );
    }

    fn repeated_decisions(bytes: &[u8], template: &str, count: usize) -> DmnIrArtifact {
        let mut artifact = DmnIrArtifact::decode(bytes).unwrap();
        let model = &mut artifact.models[0];
        let product = model
            .decisions
            .iter()
            .find(|decision| decision.identity.as_ref().unwrap().id == template)
            .unwrap()
            .clone();
        model.decisions.clear();
        artifact.evaluation_order.clear();
        for index in 0..count {
            let id = format!("{template}-{index}");
            let mut decision = product.clone();
            let identity = decision.identity.as_mut().unwrap();
            identity.id = id.clone();
            identity.name = id.clone();
            decision.variable.as_mut().unwrap().name = id.clone();
            model.decisions.push(decision);
            artifact.evaluation_order.push(DmnIrReference {
                model_namespace: model.namespace.clone(),
                element_id: id,
            });
        }
        artifact
    }

    fn decimal_literal(coefficient: String) -> DmnIrExpression {
        DmnIrExpression {
            r#type: DmnIrScalarType::ScalarTypeNumber as i32,
            node: Some(Node::Literal(DmnIrLiteral {
                value: Some(Value::NumberValue(DmnIrDecimal {
                    unscaled_value: coefficient,
                    scale: Some(0),
                })),
            })),
            ..Default::default()
        }
    }

    fn multiply(left: DmnIrExpression, right: DmnIrExpression) -> DmnIrExpression {
        binary(DmnIrBinaryOperator::BinaryOperatorMultiply, left, right)
    }

    fn binary(
        operator: DmnIrBinaryOperator,
        left: DmnIrExpression,
        right: DmnIrExpression,
    ) -> DmnIrExpression {
        DmnIrExpression {
            r#type: DmnIrScalarType::ScalarTypeNumber as i32,
            node: Some(Node::Binary(Box::new(DmnIrBinaryExpression {
                operator: operator as i32,
                left: Some(Box::new(left)),
                right: Some(Box::new(right)),
            }))),
            ..Default::default()
        }
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
    ty: Type,
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
        (Some(0), Some(2), "finmsg-dmnir-records/0")
            if artifact.required_capabilities
                == ["dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records"] =>
        {
            true
        }
        (Some(0), Some(3), "finmsg-dmnir-local-bindings/0")
            if artifact.required_capabilities
                == [
                    "dmnir.scalar",
                    "dmnir.scalar-expressions",
                    "dmnir.records",
                    "dmnir.local-bindings",
                ] =>
        {
            true
        }
        (Some(0), Some(4), "finmsg-dmnir-dates/0")
            if artifact.required_capabilities
                == [
                    "dmnir.scalar",
                    "dmnir.scalar-expressions",
                    "dmnir.records",
                    "dmnir.local-bindings",
                    "dmnir.date",
                ] =>
        {
            true
        }
        (Some(0), Some(5), "finmsg-dmnir-tables/0")
            if artifact.required_capabilities
                == [
                    "dmnir.scalar",
                    "dmnir.scalar-expressions",
                    "dmnir.records",
                    "dmnir.local-bindings",
                    "dmnir.date",
                    "dmnir.tables",
                ] =>
        {
            true
        }
        (Some(0), Some(6), "finmsg-dmnir-field-constraints/0")
            if artifact.required_capabilities
                == [
                    "dmnir.scalar",
                    "dmnir.scalar-expressions",
                    "dmnir.records",
                    "dmnir.local-bindings",
                    "dmnir.date",
                    "dmnir.tables",
                    "dmnir.field-constraints",
                ] =>
        {
            true
        }
        (Some(0), Some(7), "finmsg-dmnir-scalar-collections/0")
            if artifact.required_capabilities
                == [
                    "dmnir.scalar",
                    "dmnir.scalar-expressions",
                    "dmnir.records",
                    "dmnir.local-bindings",
                    "dmnir.date",
                    "dmnir.tables",
                    "dmnir.field-constraints",
                    "dmnir.scalar-collections",
                ] =>
        {
            true
        }
        _ => return Err("unsupported DMNIR profile"),
    };
    let mut model_ids = BTreeMap::new();
    let mut models = BTreeMap::new();
    let mut declarations = BTreeMap::new();
    let types = Types::new(
        &artifact.models,
        version.minor >= Some(2),
        version.minor >= Some(4),
        version.minor >= Some(7),
    )?;
    let constraint_source = constraints::generate(&types, version.minor >= Some(6))?;
    let mut type_expansion = types.expansion;
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
        let mut type_names = BTreeSet::new();
        for record in &model.record_definitions {
            let identity = record.identity.as_ref().ok_or("missing record identity")?;
            identity_fields(identity, &mut ids)?;
            if !type_names.insert(identity.name.as_str()) {
                return Err("duplicate record name");
            }
            for field in &record.fields {
                if let Some(id) = &field.id
                    && !ids.insert(nonempty(id)?)
                {
                    return Err("duplicate record field ID");
                }
            }
        }
        for collection in &model.collection_definitions {
            let identity = collection
                .identity
                .as_ref()
                .ok_or("missing collection identity")?;
            identity_fields(identity, &mut ids)?;
            if !type_names.insert(identity.name.as_str()) {
                return Err("duplicate collection name");
            }
        }
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
            let ty = types.variable(variable, ns)?;
            types.charge(ty, &mut type_expansion)?;
            declarations.insert((ns, identity.id.as_str()), Declaration { ty, decision });
        }
        model_ids.insert(
            ns,
            ids.into_iter().map(str::to_owned).collect::<BTreeSet<_>>(),
        );
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
    source.push_str(include_str!("date.rs"));
    source.push_str(include_str!("generated_prelude.rs.txt"));
    if version.minor >= Some(2) {
        source.push_str(include_str!("generated_records.rs.txt"));
        source.push_str(
            "fn record_fields(index: usize) -> &'static [(&'static str, Type)] { match index {\n",
        );
        for (index, record) in types.records.iter().enumerate() {
            writeln!(source, "{index} => &[").unwrap();
            for (name, ty) in &record.fields {
                writeln!(source, "({name:?}, {}),", ty.source()).unwrap();
            }
            source.push_str("],\n");
        }
        source.push_str("_ => unreachable!(\"verified record index\"), } }\n");
    } else {
        source.push_str("fn bind_record(_: &Value, _: usize) -> Result<Value, &'static str> { Err(\"unsupported record type\") }\n");
    }
    if structured {
        source.push_str(include_str!("generated_expressions.rs.txt"));
        source.push_str(include_str!("generated_division.rs.txt"));
    }
    if version.minor >= Some(5) {
        source.push_str(include_str!("generated_tables.rs.txt"));
    }
    source.push_str("fn collection_element(index: usize) -> Type { match index {\n");
    for (index, scalar) in types.collections.iter().enumerate() {
        writeln!(source, "{index} => Type::Scalar({}),", *scalar as i32).unwrap();
    }
    source.push_str("_ => unreachable!(\"verified collection index\"), } }\n");
    source.push_str(&constraint_source);
    source.push_str("pub fn evaluate(inputs: &BTreeMap<Key<'_>, Value>) -> Result<BTreeMap<Key<'static>, Value>, &'static str> {\n");
    let mut ready = BTreeSet::new();
    let mut numeric_bounds = BTreeMap::new();
    let mut arithmetic_depths = BTreeMap::new();
    let mut limb_products = 0;
    let mut division_limb_visits = 0;
    for (&key, declaration) in &declarations {
        if declaration.decision.is_none() {
            ready.insert(key);
            if declaration.ty == DmnIrScalarType::ScalarTypeNumber {
                numeric_bounds.insert(key, NumericBounds::INPUT);
            }
            writeln!(
                source,
                "    let v{} = bind(inputs.get(&{key:?}).ok_or(\"missing input\")?, {})?;",
                slots[&key],
                declaration.ty.source()
            )
            .unwrap();
            if version.minor >= Some(6) {
                writeln!(
                    source,
                    "check_constraints(&v{}, {})?;",
                    slots[&key],
                    declaration.ty.source()
                )
                .unwrap();
            }
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
        if decision.expression.is_some() == decision.decision_table.is_some() {
            return Err("decision requires exactly one logic body");
        }
        if decision.decision_table.is_some() && version.minor < Some(5) {
            return Err("decision tables require profile 0.5");
        }
        let mut emitter = ExpressionEmitter {
            structured,
            table_cell: false,
            collections_enabled: version.minor >= Some(7),
            dates: version.minor >= Some(4),
            locals_enabled: version.minor >= Some(3),
            locals: BTreeMap::new(),
            binding_ids: BTreeSet::new(),
            source_ids: model_ids.get_mut(key.0).unwrap(),
            dependencies: &dependencies,
            declarations: &declarations,
            slots: &slots,
            arithmetic_depths: &arithmetic_depths,
            numeric_bounds: &numeric_bounds,
            limb_products: &mut limb_products,
            division_limb_visits: &mut division_limb_visits,
            types: &types,
            type_expansion: &mut type_expansion,
            namespace: key.0,
        };
        let Emitted {
            ty,
            value,
            depth,
            bounds,
        } = if let Some(table) = &decision.decision_table {
            emitter.table(table, declaration.ty)?
        } else {
            emitter.expression(decision.expression.as_ref().unwrap())?
        };
        if !assignable(ty, declaration.ty) {
            return Err("decision expression type mismatch");
        }
        arithmetic_depths.insert(key, depth);
        if let Some(bounds) = bounds {
            numeric_bounds.insert(key, bounds);
        }
        writeln!(source, "    let v{} = {value};", slots[&key]).unwrap();
        if version.minor >= Some(6) {
            writeln!(
                source,
                "check_constraints(&v{}, {})?;",
                slots[&key],
                declaration.ty.source()
            )
            .unwrap();
        }
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
    table_cell: bool,
    dates: bool,
    collections_enabled: bool,
    locals_enabled: bool,
    locals: BTreeMap<String, Emitted>,
    binding_ids: BTreeSet<String>,
    source_ids: &'a mut BTreeSet<String>,
    dependencies: &'a BTreeSet<Key<'model>>,
    declarations: &'a BTreeMap<Key<'model>, Declaration<'model>>,
    slots: &'a BTreeMap<Key<'model>, usize>,
    arithmetic_depths: &'a BTreeMap<Key<'model>, usize>,
    numeric_bounds: &'a BTreeMap<Key<'model>, NumericBounds>,
    limb_products: &'a mut usize,
    division_limb_visits: &'a mut usize,
    types: &'a Types<'model>,
    type_expansion: &'a mut usize,
    namespace: &'a str,
}

#[derive(Clone)]
struct Emitted {
    ty: Type,
    value: String,
    depth: usize,
    bounds: Option<NumericBounds>,
}

impl ExpressionEmitter<'_, '_> {
    fn expression(&mut self, expression: &DmnIrExpression) -> Check<Emitted> {
        use DmnIrScalarType::{
            ScalarTypeBoolean as Boolean, ScalarTypeNumber as Number, ScalarTypeString as Text,
        };
        use DmnIrUnaryOperator::{UnaryOperatorNegate, UnaryOperatorNot, UnaryOperatorPositive};

        if self.table_cell
            && (expression.collection_type.is_some()
                || matches!(expression.node, Some(Node::Context(_))))
        {
            return Err("contexts are unsupported in table cells");
        }
        let ty = self.types.expression(expression, self.namespace)?;
        self.types.charge(ty, self.type_expansion)?;
        let mut depth = 0;
        let mut bounds = None;
        let value = match expression.node.as_ref().ok_or("missing expression node")? {
            Node::List(list) if self.collections_enabled => {
                let Type::Collection(index) = ty else {
                    return Err("list requires a named collection type");
                };
                let mut elements = Vec::new();
                for element in &list.elements {
                    let emitted = self.expression(element)?;
                    if !matches!(emitted.ty, Type::Scalar(_))
                        || !assignable(emitted.ty, self.types.collections[index])
                    {
                        return Err("list element type mismatch");
                    }
                    depth = depth.max(emitted.depth);
                    elements.push(emitted.value);
                }
                format!("Value::List(vec![{}])", elements.join(","))
            }
            Node::Literal(literal) => {
                if matches!(ty, Type::Collection(_)) {
                    return Err("collection literal requires a list constructor or scalar null");
                }
                let (source, literal_bounds) = literal_source(literal, ty, self.dates)?;
                bounds = literal_bounds;
                source
            }
            Node::GlobalReference(reference) => {
                let target = key(reference);
                if !self.dependencies.contains(&target) {
                    return Err("expression reference is not a declared dependency");
                }
                if if matches!(ty, Type::Collection(_)) {
                    self.declarations[&target].ty != ty
                } else {
                    !assignable(self.declarations[&target].ty, ty)
                } {
                    return Err("reference type mismatch");
                }
                depth = self.arithmetic_depths.get(&target).copied().unwrap_or(0);
                bounds = self.numeric_bounds.get(&target).copied();
                format!("v{}.clone()", self.slots[&target])
            }
            Node::LocalReference(reference) if self.locals_enabled => {
                let local = self
                    .locals
                    .get(&reference.binding_id)
                    .ok_or("local binding is not visible")?;
                if local.ty != ty {
                    return Err("local reference type mismatch");
                }
                depth = local.depth;
                bounds = local.bounds;
                local.value.clone()
            }
            Node::Context(context) if self.locals_enabled => {
                let outer = self.locals.clone();
                let mut block = String::from("{ ");
                let mut names = BTreeSet::new();
                for binding in &context.bindings {
                    let id = nonempty(&binding.binding_id)?;
                    if !self.binding_ids.insert(id.to_owned()) {
                        return Err("duplicate local binding ID");
                    }
                    let variable = binding.variable.as_ref().ok_or("missing local variable")?;
                    if !names.insert(nonempty(&variable.name)?) {
                        return Err("duplicate context variable name");
                    }
                    if let Some(id) = &variable.id {
                        if !self.source_ids.insert(nonempty(id)?.to_owned()) {
                            return Err("duplicate variable ID");
                        }
                    }
                    let declared = self.types.variable(variable, self.namespace)?;
                    self.types.charge(declared, self.type_expansion)?;
                    let slot = self.binding_ids.len();
                    let mut value =
                        self.expression(binding.value.as_ref().ok_or("missing local value")?)?;
                    if !assignable(value.ty, declared) {
                        return Err("local binding type mismatch");
                    }
                    depth = depth.max(value.depth);
                    write!(block, "let l{slot} = {}; ", value.value).unwrap();
                    value.value = format!("l{slot}.clone()");
                    value.ty = declared;
                    self.locals.insert(id.to_owned(), value);
                }
                let result =
                    self.expression(context.result.as_deref().ok_or("missing context result")?)?;
                if !assignable(result.ty, ty) {
                    return Err("context result type mismatch");
                }
                depth = depth.max(result.depth);
                bounds = result.bounds;
                write!(block, "{} }}", result.value).unwrap();
                self.locals = outer;
                block
            }
            Node::FieldAccess(access) => {
                let source =
                    self.expression(access.source.as_deref().ok_or("missing field source")?)?;
                let Type::Record(index) = source.ty else {
                    return Err("field access requires a record source");
                };
                let (_, field_type) = self.types.records[index]
                    .fields
                    .iter()
                    .find(|(name, _)| *name == access.field_name)
                    .ok_or("record field does not exist")?;
                if *field_type != ty {
                    return Err("record field result type mismatch");
                }
                depth = source.depth;
                if ty == Number {
                    bounds = Some(NumericBounds::INPUT);
                }
                format!("field({}, {:?})", source.value, access.field_name)
            }
            Node::Unary(unary) if self.structured => {
                let Emitted {
                    ty: operand_type,
                    value: operand,
                    depth: operand_depth,
                    bounds: operand_bounds,
                } = self.expression(unary.operand.as_deref().ok_or("missing unary operand")?)?;
                depth = operand_depth;
                match DmnIrUnaryOperator::try_from(unary.operator) {
                    Ok(UnaryOperatorPositive) if ty == Number && operand_type == Number => {
                        bounds = operand_bounds;
                        operand
                    }
                    Ok(UnaryOperatorNegate) if ty == Number && operand_type == Number => {
                        bounds = operand_bounds;
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
                let Emitted {
                    ty: left_type,
                    value: left,
                    depth: left_depth,
                    bounds: left_bounds,
                } = self.expression(binary.left.as_deref().ok_or("missing left operand")?)?;
                let Emitted {
                    ty: right_type,
                    value: right,
                    depth: right_depth,
                    bounds: right_bounds,
                } = self.expression(binary.right.as_deref().ok_or("missing right operand")?)?;
                depth = left_depth.max(right_depth);
                let logical = assignable(left_type, Boolean) && assignable(right_type, Boolean);
                let ordered = left_type == right_type
                    && (left_type == Number
                        || left_type == Text
                        || self.dates && left_type == DmnIrScalarType::ScalarTypeDate);
                let numeric = ty == Number && left_type == Number && right_type == Number;
                match DmnIrBinaryOperator::try_from(binary.operator) {
                    Ok(op @ (BinaryOperatorAdd | BinaryOperatorSubtract)) if numeric => {
                        depth += 1;
                        if depth > MAX_ARITHMETIC_CHAIN {
                            return Err("decimal arithmetic chain exceeds 128 operations");
                        }
                        bounds = left_bounds.zip(right_bounds).map(|(a, b)| a.sum(b));
                        let subtract = op == BinaryOperatorSubtract;
                        format!("decimal_arithmetic({left}, {right}, {subtract})")
                    }
                    Ok(BinaryOperatorMultiply) if numeric => {
                        if let Some((a, b)) = left_bounds.zip(right_bounds) {
                            bounds = Some(a.product(b));
                            *self.limb_products += a.limb_products(b);
                            if *self.limb_products > MAX_LIMB_PRODUCTS {
                                return Err(
                                    "decimal multiplication work bound exceeds 8388608 limb products",
                                );
                            }
                        }
                        format!("multiply({left}, {right})")
                    }
                    Ok(BinaryOperatorDivide) if numeric => {
                        if let Some((a, b)) = left_bounds.zip(right_bounds) {
                            bounds = a.quotient(b);
                            *self.division_limb_visits += a.division_limb_visits(b);
                            if *self.division_limb_visits > MAX_DIVISION_LIMB_VISITS {
                                return Err(
                                    "decimal division work bound exceeds 8388608 limb visits",
                                );
                            }
                        }
                        format!("divide({left}, {right})")
                    }
                    _ if ty != Boolean => return Err("unsupported binary signature"),
                    Ok(BinaryOperatorEqual)
                        if matches!(left_type, Type::Scalar(_))
                            && matches!(right_type, Type::Scalar(_)) =>
                    {
                        format!("equal({left}, {right})")
                    }
                    Ok(BinaryOperatorNotEqual)
                        if matches!(left_type, Type::Scalar(_))
                            && matches!(right_type, Type::Scalar(_)) =>
                    {
                        format!("not(equal({left}, {right}))")
                    }
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
                let Emitted {
                    ty: condition_type,
                    value: condition,
                    depth: condition_depth,
                    ..
                } = self.expression(
                    conditional
                        .condition
                        .as_deref()
                        .ok_or("missing condition")?,
                )?;
                let Emitted {
                    ty: then_type,
                    value: then_value,
                    depth: then_depth,
                    bounds: then_bounds,
                } = self.expression(
                    conditional
                        .then_expression
                        .as_deref()
                        .ok_or("missing then expression")?,
                )?;
                let Emitted {
                    ty: else_type,
                    value: else_value,
                    depth: else_depth,
                    bounds: else_bounds,
                } = self.expression(
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
                depth = condition_depth.max(then_depth).max(else_depth);
                bounds = match (then_bounds, else_bounds) {
                    (Some(a), Some(b)) => Some(a.join(b)),
                    (a, b) => a.or(b),
                };
                format!(
                    "if matches!({condition}, Value::Boolean(true)) {{ {then_value} }} else {{ {else_value} }}"
                )
            }
            _ => return Err("expression outside scalar profile"),
        };
        if let Some(bounds) = bounds {
            bounds.verify()?;
        }
        Ok(Emitted {
            ty,
            value,
            depth,
            bounds,
        })
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

fn assignable(actual: impl Into<Type>, expected: impl Into<Type>) -> bool {
    let actual = actual.into();
    actual == expected.into() || actual == DmnIrScalarType::ScalarTypeNull
}

fn literal_source(
    literal: &DmnIrLiteral,
    expected: Type,
    dates: bool,
) -> Check<(String, Option<NumericBounds>)> {
    use DmnIrScalarType::*;
    let mut bounds = None;
    let (actual, source) = match literal.value.as_ref().ok_or("missing literal value")? {
        Value::NullValue(0) => (ScalarTypeNull, "Value::Null".into()),
        Value::BooleanValue(value) => (ScalarTypeBoolean, format!("Value::Boolean({value})")),
        Value::StringValue(value) => (ScalarTypeString, format!("Value::Text({value:?}.into())")),
        Value::DateValue(value) if dates => {
            let year = value.year.ok_or("missing date year")?;
            let month = value.month.ok_or("missing date month")?;
            let day = value.day.ok_or("missing date day")?;
            if !crate::date::valid_date(year, month, day) {
                return Err("invalid civil date");
            }
            (
                ScalarTypeDate,
                format!("Value::Date {{ year: {year}, month: {month}, day: {day} }}"),
            )
        }
        Value::NumberValue(value) => {
            let scale = value.scale.ok_or("missing decimal scale")?;
            let coefficient = &value.unscaled_value;
            if !decimal::valid_decimal(coefficient, scale) {
                return Err("invalid decimal");
            }
            bounds = Some(NumericBounds::literal(coefficient, scale));
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
    Ok((source, bounds))
}
