#[path = "bkms.rs"]
mod bkms;
#[path = "closures.rs"]
mod closures;
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
                operator: DmnIrBinaryOperator::BinaryOperatorMultiply as i32,
                left: conditional.condition.clone(),
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

    #[test]
    fn rejects_invalid_filter_scopes_and_signatures() {
        let valid =
            DmnIrArtifact::decode(include_bytes!("../tests/fixtures/filters-0.8.dmnir").as_slice())
                .unwrap();
        for (edit, expected) in [
            (
                (|f: &mut DmnIrFilterExpression| f.source = None) as fn(&mut DmnIrFilterExpression),
                "missing filter source",
            ),
            (|f| f.predicate = None, "missing filter predicate"),
            (
                |f| f.item_binding_id = "".into(),
                "blank declaration or reference",
            ),
            (
                |f| {
                    let e = f.predicate.as_mut().unwrap();
                    e.r#type = 3;
                    e.node = decimal_literal("1".into()).node;
                },
                "filter predicate must be boolean",
            ),
            (
                |f| {
                    f.predicate.as_mut().unwrap().node =
                        Some(Node::Context(Box::new(DmnIrContextExpression::default())));
                },
                "contexts are unsupported in filter predicates",
            ),
            (
                |f| {
                    f.predicate.as_mut().unwrap().node = Some(Node::Filter(Box::new(f.clone())));
                },
                "filters inside predicates are unsupported",
            ),
            (
                |f| {
                    f.source.as_mut().unwrap().node =
                        Some(Node::LocalReference(DmnIrLocalReference {
                            binding_id: f.item_binding_id.clone(),
                        }));
                },
                "local binding is not visible",
            ),
            (
                |f| {
                    let nested = f.clone();
                    f.source.as_mut().unwrap().node = Some(Node::Filter(Box::new(nested)));
                },
                "duplicate filter binding ID",
            ),
            (
                |f| {
                    let e = f.source.as_mut().unwrap();
                    e.collection_type.as_mut().unwrap().element_id = "dates".into();
                    e.node = Some(Node::List(DmnIrListExpression::default()));
                },
                "filter source type mismatch",
            ),
        ] {
            let mut artifact = valid.clone();
            let root = artifact
                .models
                .iter_mut()
                .find(|m| m.namespace == "urn:collections:policy")
                .unwrap();
            let e = root
                .decisions
                .iter_mut()
                .find(|d| d.identity.as_ref().unwrap().id == "echo")
                .unwrap()
                .expression
                .as_mut()
                .unwrap();
            let Some(Node::Filter(f)) = &mut e.node else {
                panic!()
            };
            edit(f);
            assert_eq!(verify_and_generate(&artifact), Err(expected));
        }
        let mut old = valid;
        old.schema_version.as_mut().unwrap().minor = Some(7);
        old.semantics_version = "finmsg-dmnir-scalar-collections/0".into();
        old.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&old),
            Err("expression outside scalar profile")
        );
    }

    #[test]
    fn filter_items_retain_computed_list_numeric_bounds() {
        let mut artifact =
            DmnIrArtifact::decode(include_bytes!("../tests/fixtures/filters-0.8.dmnir").as_slice())
                .unwrap();
        let root = artifact
            .models
            .iter_mut()
            .find(|m| m.namespace == "urn:collections:policy")
            .unwrap();
        let e = root
            .decisions
            .iter_mut()
            .find(|d| d.identity.as_ref().unwrap().id == "echo")
            .unwrap()
            .expression
            .as_mut()
            .unwrap();
        let Some(Node::Filter(f)) = &mut e.node else {
            panic!()
        };
        let mut tiny = decimal_literal("1".into());
        let Some(Node::Literal(literal)) = &mut tiny.node else {
            panic!()
        };
        let Some(Value::NumberValue(value)) = &mut literal.value else {
            panic!()
        };
        value.scale = Some(4096);
        f.source.as_mut().unwrap().node = Some(Node::List(DmnIrListExpression {
            elements: vec![multiply(tiny.clone(), tiny)],
        }));
        let item = DmnIrExpression {
            r#type: 3,
            node: Some(Node::LocalReference(DmnIrLocalReference {
                binding_id: f.item_binding_id.clone(),
            })),
            ..Default::default()
        };
        let mut tenth = decimal_literal("1".into());
        let Some(Node::Literal(literal)) = &mut tenth.node else {
            panic!()
        };
        let Some(Value::NumberValue(value)) = &mut literal.value else {
            panic!()
        };
        value.scale = Some(1);
        let mut comparison = binary(
            DmnIrBinaryOperator::BinaryOperatorGreater,
            multiply(item, tenth),
            decimal_literal("0".into()),
        );
        comparison.r#type = 2;
        f.predicate = Some(Box::new(comparison));
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal scale bound exceeds 8192")
        );
    }

    #[test]
    fn validates_fixed_builtin_signatures_and_profile() {
        let valid = DmnIrArtifact::decode(
            include_bytes!("../tests/fixtures/builtins-0.9.dmnir").as_slice(),
        )
        .unwrap();
        for (edit, expected) in [
            (
                (|c: &mut DmnIrBuiltinCall| c.operation = 0) as fn(&mut DmnIrBuiltinCall),
                "unsupported builtin operation",
            ),
            (|c| c.arguments.clear(), "builtin argument count mismatch"),
            (
                |c| c.arguments.push(c.arguments[0].clone()),
                "builtin argument count mismatch",
            ),
            (
                |c| {
                    c.arguments[0] = DmnIrExpression {
                        r#type: 2,
                        node: Some(Node::Literal(DmnIrLiteral {
                            value: Some(Value::BooleanValue(true)),
                        })),
                        ..Default::default()
                    }
                },
                "builtin argument type mismatch",
            ),
            (|c| c.operation = 2, "builtin argument type mismatch"),
            (|c| c.operation = 3, "builtin result type mismatch"),
        ] {
            let mut artifact = valid.clone();
            let e = artifact.models[0]
                .decisions
                .iter_mut()
                .find(|d| d.identity.as_ref().unwrap().id == "absolute")
                .unwrap()
                .expression
                .as_mut()
                .unwrap();
            let Some(Node::BuiltinCall(call)) = &mut e.node else {
                panic!()
            };
            edit(call);
            assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
            assert_eq!(verify_and_generate(&artifact), Err(expected));
        }
        let mut old = valid;
        old.schema_version.as_mut().unwrap().minor = Some(8);
        old.semantics_version = "finmsg-dmnir-scalar-filters/0".into();
        old.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&old),
            Err("expression outside scalar profile")
        );
    }

    #[test]
    fn abs_preserves_computed_operand_bounds() {
        let mut artifact = DmnIrArtifact::decode(
            include_bytes!("../tests/fixtures/builtins-0.9.dmnir").as_slice(),
        )
        .unwrap();
        let e = artifact.models[0]
            .decisions
            .iter_mut()
            .find(|d| d.identity.as_ref().unwrap().id == "absolute")
            .unwrap()
            .expression
            .as_mut()
            .unwrap();
        let mut tiny = decimal_literal("1".into());
        let Some(Node::Literal(literal)) = &mut tiny.node else {
            panic!()
        };
        let Some(Value::NumberValue(value)) = &mut literal.value else {
            panic!()
        };
        value.scale = Some(4096);
        let Some(Node::BuiltinCall(call)) = &mut e.node else {
            panic!()
        };
        call.arguments[0] = multiply(tiny.clone(), tiny);
        let mut tenth = decimal_literal("1".into());
        let Some(Node::Literal(literal)) = &mut tenth.node else {
            panic!()
        };
        let Some(Value::NumberValue(value)) = &mut literal.value else {
            panic!()
        };
        value.scale = Some(1);
        *e = multiply(e.clone(), tenth);
        assert_eq!(
            verify_and_generate(&artifact),
            Err("decimal scale bound exceeds 8192")
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
        (Some(0), Some(8), "finmsg-dmnir-scalar-filters/0")
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
                    "dmnir.scalar-filters",
                ] =>
        {
            true
        }
        (Some(0), Some(9), "finmsg-dmnir-builtin-calls/0")
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
                    "dmnir.scalar-filters",
                    "dmnir.builtin-calls",
                ] =>
        {
            true
        }
        (Some(0), Some(10), "finmsg-dmnir-named-feel-bkms/0")
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
                    "dmnir.scalar-filters",
                    "dmnir.builtin-calls",
                    "dmnir.named-feel-bkms",
                ] =>
        {
            true
        }
        (Some(0), Some(11), "finmsg-dmnir-inline-feel/0")
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
                    "dmnir.scalar-filters",
                    "dmnir.builtin-calls",
                    "dmnir.named-feel-bkms",
                    "dmnir.inline-feel",
                ] =>
        {
            true
        }
        _ => return Err("unsupported DMNIR profile"),
    };
    let mut bkm_declarations = BTreeMap::new();
    let mut model_ids = BTreeMap::new();
    let mut models = BTreeMap::new();
    let mut declarations = BTreeMap::new();
    let extended = version.minor >= Some(9);
    let types = Types::new(
        &artifact.models,
        version.minor >= Some(2),
        version.minor >= Some(4),
        version.minor >= Some(7),
        extended,
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
        bkms::register(
            model,
            &types,
            version.minor >= Some(10),
            &mut ids,
            &mut names,
            &mut variable_names,
            &mut type_expansion,
            &mut bkm_declarations,
        )?;
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
    let knowledge_graph = bkms::graph(&bkm_declarations, &imports)?;
    let mut expanded_nodes = 0;
    let mut call_serial = 0;
    let slots: BTreeMap<_, _> = declarations
        .keys()
        .copied()
        .enumerate()
        .map(|(slot, key)| (key, slot))
        .collect();
    let mut source = String::from(include_str!("decimal.rs"));
    source.push('\n');
    source.push_str(include_str!("date.rs"));
    writeln!(source, "const DYNAMIC_ANY: bool = {extended};").unwrap();
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
        source.push_str("fn bind_record(_: &Value, _: usize) -> Result<Value, &'static str> { Err(\"unsupported record type\") }\nfn record_fields(_: usize) -> &'static [(&'static str, Type)] { &[] }\n");
    }
    if structured {
        source.push_str(include_str!("generated_expressions.rs.txt"));
        source.push_str(include_str!("generated_division.rs.txt"));
        source.push_str(include_str!("generated_runtime_ext.rs.txt"));
    }
    if version.minor >= Some(5) {
        source.push_str(include_str!("generated_tables.rs.txt"));
    }
    source.push_str("fn collection_element(index: usize) -> Type { match index {\n");
    for (index, item_type) in types.collections.iter().enumerate() {
        writeln!(source, "{index} => {},", item_type.source()).unwrap();
    }
    source.push_str("_ => unreachable!(\"verified collection index\"), } }\n");
    if structured {
        source.push_str(include_str!("generated_filters.rs.txt"));
    }
    if version.minor >= Some(9) {
        source.push_str(include_str!("generated_builtins.rs.txt"));
    }
    source.push_str(&constraint_source);
    let mut generated_functions: Vec<(usize, Vec<String>, bool, String)> = Vec::new();
    let functions_position = source.len();
    source.push_str("pub fn evaluate(inputs: &BTreeMap<Key<'_>, Value>) -> Result<BTreeMap<Key<'static>, Value>, &'static str> {\n");
    let filter_budget_position = source.len();
    let mut ready = BTreeSet::new();
    let mut numeric_bounds = BTreeMap::new();
    let mut arithmetic_depths = BTreeMap::new();
    let mut limb_products = 0;
    let mut division_limb_visits = 0;
    let mut bkm_fn_entries: Vec<(String, String, Vec<String>, Vec<String>, String, String)> =
        Vec::new();
    for (&owner, bkm) in &bkm_declarations {
        let mut products = 0;
        let mut divisions = 0;
        let mut emitter = ExpressionEmitter {
            structured,
            closures_enabled: version.minor >= Some(11),
            closures: BTreeMap::new(),
            in_closure: false,
            unused_captures: None,
            bkms_enabled: true,
            table_cell: false,
            filters_enabled: true,
            builtins_enabled: true,
            collections_enabled: true,
            dates: true,
            locals_enabled: true,
            filter_predicate: false,
            validate_ids: true,
            expression_depth: 0,
            bkms: &bkm_declarations,
            knowledge_graph: &knowledge_graph,
            knowledge: knowledge_graph[&owner].clone(),
            call_serial: &mut call_serial,
            expanded_nodes: &mut expanded_nodes,
            locals: BTreeMap::new(),
            binding_ids: BTreeSet::new(),
            source_ids: model_ids.get_mut(owner.0).unwrap(),
            dependencies: BTreeSet::new(),
            declarations: &declarations,
            slots: &slots,
            arithmetic_depths: &arithmetic_depths,
            numeric_bounds: &numeric_bounds,
            limb_products: &mut products,
            division_limb_visits: &mut divisions,
            types: &types,
            type_expansion: &mut type_expansion,
            namespace: owner.0,
            functions: &mut generated_functions,
        };
        let mut param_names = Vec::new();
        for (p_idx, (parameter, ty)) in bkm.source.parameters.iter().zip(&bkm.parameters).enumerate() {
            emitter.binding_ids.insert(parameter.binding_id.clone());
            let pname = parameter
                .variable
                .as_ref()
                .map(|v| v.name.clone())
                .unwrap_or_default();
            param_names.push(pname);
            emitter.locals.insert(
                parameter.binding_id.clone(),
                Emitted {
                    ty: *ty,
                    value: format!("bkm_args[{p_idx}].clone()"),
                    depth: 0,
                    bounds: bkms::input_bounds(*ty, &types),
                },
            );
        }
        let result = emitter.expression(bkm.source.body.as_ref().ok_or("missing BKM body")?)?;
        if !extended && !assignable(result.ty, bkm.ty) {
            return Err("BKM body type mismatch");
        }
        if let Some(identity) = &bkm.source.identity {
            let var_name = bkm
                .source
                .variable
                .as_ref()
                .map(|v| v.name.clone())
                .unwrap_or_else(|| identity.name.clone());
            bkm_fn_entries.push((
                identity.name.clone(),
                var_name,
                param_names,
                bkm.parameters.iter().map(|t| t.source()).collect::<Vec<_>>(),
                bkm.ty.source(),
                result.value,
            ));
        }
    }
    for (&key, declaration) in &declarations {
        if declaration.decision.is_none() {
            ready.insert(key);
            if declaration.ty == DmnIrScalarType::ScalarTypeNumber
                || matches!(declaration.ty, Type::Collection(index) if types.collections[index] == DmnIrScalarType::ScalarTypeNumber)
            {
                numeric_bounds.insert(key, NumericBounds::INPUT);
            }
            if extended {
                writeln!(
                    source,
                    "    let v{} = bind(inputs.get(&{key:?}).unwrap_or(&Value::Null), {})?;",
                    slots[&key],
                    declaration.ty.source()
                )
                .unwrap();
            } else {
                writeln!(
                    source,
                    "    let v{} = bind(inputs.get(&{key:?}).ok_or(\"missing input\")?, {})?;",
                    slots[&key],
                    declaration.ty.source()
                )
                .unwrap();
            }
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
    if !extended {
        writeln!(
            source,
            "    if inputs.len() != {} {{ return Err(\"unexpected input\"); }}",
            ready.len()
        )
        .unwrap();
    }
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
            closures_enabled: version.minor >= Some(11),
            closures: BTreeMap::new(),
            in_closure: false,
            unused_captures: None,
            table_cell: false,
            bkms_enabled: version.minor >= Some(10),
            builtins_enabled: version.minor >= Some(9),
            filters_enabled: version.minor >= Some(8),
            filter_predicate: false,
            collections_enabled: version.minor >= Some(7),
            dates: version.minor >= Some(4),
            locals_enabled: version.minor >= Some(3),
            locals: BTreeMap::new(),
            binding_ids: BTreeSet::new(),
            source_ids: model_ids.get_mut(key.0).unwrap(),
            validate_ids: true,
            expression_depth: 0,
            bkms: &bkm_declarations,
            knowledge_graph: &knowledge_graph,
            knowledge: bkms::knowledge(
                key,
                &decision.knowledge_dependencies,
                &bkm_declarations,
                &imports,
                version.minor >= Some(10),
            )?,
            call_serial: &mut call_serial,
            expanded_nodes: &mut expanded_nodes,
            dependencies: dependencies.clone(),
            declarations: &declarations,
            slots: &slots,
            arithmetic_depths: &arithmetic_depths,
            numeric_bounds: &numeric_bounds,
            limb_products: &mut limb_products,
            division_limb_visits: &mut division_limb_visits,
            types: &types,
            type_expansion: &mut type_expansion,
            namespace: key.0,
            functions: &mut generated_functions,
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
        if !extended && !assignable(ty, declaration.ty) {
            return Err("decision expression type mismatch");
        }
        arithmetic_depths.insert(key, depth);
        if let Some(bounds) = bounds {
            numeric_bounds.insert(key, bounds);
        }
        if extended {
            writeln!(
                source,
                "    let v{} = coerce_output({value}, {});",
                slots[&key],
                declaration.ty.source()
            )
            .unwrap();
        } else {
            writeln!(source, "    let v{} = {value};", slots[&key]).unwrap();
        }
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
    if structured {
        source.insert_str(filter_budget_position, &format!("let mut filter_budget = FilterBudget {{ visits: 0, products: {limb_products}, divisions: {division_limb_visits} }};\n"));
    }
    source.push_str("    Ok(results)\n}\n");

    // Emit call_function dispatcher for first-class closures (Node::Closure / DynamicInvocation)
    let mut fn_src = String::new();
    if structured {
        fn_src.push_str("fn call_function(id: usize, captures: &[Value], pos_args: &[Value], named_args: &[(&str, Value)], mut filter_budget: &mut FilterBudget) -> Result<Value, &'static str> {\n    match id {\n");
        for (id, params, external, body_src) in &generated_functions {
            writeln!(fn_src, "        {id} => {{").unwrap();
            let param_slice: Vec<String> = params.iter().map(|p| format!("{p:?}")).collect();
            writeln!(
                fn_src,
                "            let Some(args) = bind_call_args(&[{}], pos_args, named_args) else {{ return Ok(Value::Null); }};",
                param_slice.join(", ")
            )
            .unwrap();
            if *external {
                writeln!(fn_src, "            let desc = {body_src};").unwrap();
                writeln!(fn_src, "            Ok(eval_external_function(&desc, &args))").unwrap();
            } else {
                writeln!(fn_src, "            Ok({body_src})").unwrap();
            }
            fn_src.push_str("        }\n");
        }
        fn_src.push_str("        _ => Ok(Value::Null),\n    }\n}\n");
    }
    source.insert_str(functions_position, &fn_src);

    // Emit evaluate_invocable for TCK direct BKM and DecisionService invocations
    source.push_str("pub fn evaluate_invocable(name: &str, inputs: &BTreeMap<Key<'_>, Value>, named_inputs: &BTreeMap<String, Value>) -> Result<Value, &'static str> {\n");
    if version.minor >= Some(8) {
        source.push_str("    let mut filter_budget = FilterBudget { visits: 0, products: 0, divisions: 0 };\n");
    }
    source.push_str("    match name {\n");
    for (id_name, var_name, param_names, param_types, ret_type, body_src) in &bkm_fn_entries {
        if id_name == var_name {
            writeln!(source, "        {id_name:?} => {{").unwrap();
        } else {
            writeln!(source, "        {id_name:?} | {var_name:?} => {{").unwrap();
        }
        writeln!(source, "            let param_names: &[&str] = &[{}];", param_names.iter().map(|p| format!("{p:?}")).collect::<Vec<_>>().join(", ")).unwrap();
        writeln!(source, "            let param_types: &[Type] = &[{}];", param_types.join(", ")).unwrap();
        source.push_str("            let mut bkm_args: Vec<Value> = Vec::with_capacity(param_names.len());\n");
        source.push_str("            let mut missing = false;\n");
        source.push_str("            for (i, &pname) in param_names.iter().enumerate() {\n");
        if extended {
            source.push_str("                if let Some(v) = named_inputs.get(pname) { bkm_args.push(coerce_output(v.clone(), param_types[i])); } else { missing = true; bkm_args.push(Value::Null); }\n");
        } else {
            source.push_str("                if let Some(v) = named_inputs.get(pname) { bkm_args.push(v.clone()); } else { missing = true; bkm_args.push(Value::Null); }\n");
        }
        source.push_str("            }\n");
        source.push_str("            if (missing && !named_inputs.is_empty()) || (named_inputs.is_empty() && !param_names.is_empty()) { return Ok(Value::Null); }\n");
        if extended {
            writeln!(source, "            Ok(coerce_output({body_src}, {ret_type}))").unwrap();
        } else {
            writeln!(source, "            Ok({body_src})").unwrap();
        }
        source.push_str("        }\n");
    }
    // Decision services
    for model in &artifact.models {
        for ds in &model.decision_services {
            if let Some(identity) = &ds.identity {
                let ds_name = &identity.name;
                let var_name = ds.variable.as_ref().map(|v| v.name.as_str()).unwrap_or(ds_name);
                if ds_name == var_name {
                    writeln!(source, "        {ds_name:?} => {{").unwrap();
                } else {
                    writeln!(source, "        {ds_name:?} | {var_name:?} => {{").unwrap();
                }
                source.push_str("            let mut results = evaluate(inputs)?;\n");
                if ds.output_decisions.len() == 1 {
                    let out_ref = &ds.output_decisions[0];
                    let k = (out_ref.model_namespace.as_str(), out_ref.element_id.as_str());
                    writeln!(source, "            Ok(results.remove(&{k:?}).unwrap_or(Value::Null))").unwrap();
                } else {
                    source.push_str("            let mut map = BTreeMap::new();\n");
                    for out_ref in &ds.output_decisions {
                        let k = (out_ref.model_namespace.as_str(), out_ref.element_id.as_str());
                        let out_name = declarations
                            .get(&k)
                            .and_then(|d| d.decision)
                            .and_then(|d| d.identity.as_ref())
                            .map(|i| i.name.as_str())
                            .unwrap_or(out_ref.element_id.as_str());
                        writeln!(source, "            map.insert({out_name:?}.to_string(), results.remove(&{k:?}).unwrap_or(Value::Null));").unwrap();
                    }
                    source.push_str("            Ok(Value::Record(map))\n");
                }
                source.push_str("        }\n");
            }
        }
    }
    source.push_str("        _ => {\n            let _ = (inputs, named_inputs);\n            Err(\"unknown invocable\")\n        }\n    }\n}\n");
    Ok(source)
}

struct ExpressionEmitter<'a, 'model> {
    structured: bool,
    closures_enabled: bool,
    closures: BTreeMap<String, closures::Closure>,
    in_closure: bool,
    unused_captures: Option<BTreeSet<String>>,
    table_cell: bool,
    filters_enabled: bool,
    builtins_enabled: bool,
    bkms_enabled: bool,
    filter_predicate: bool,
    dates: bool,
    collections_enabled: bool,
    locals_enabled: bool,
    locals: BTreeMap<String, Emitted>,
    binding_ids: BTreeSet<String>,
    source_ids: &'a mut BTreeSet<String>,
    dependencies: BTreeSet<Key<'model>>,
    bkms: &'a BTreeMap<Key<'model>, bkms::Bkm<'model>>,
    knowledge_graph: &'a BTreeMap<Key<'model>, BTreeSet<Key<'model>>>,
    knowledge: BTreeSet<Key<'model>>,
    call_serial: &'a mut usize,
    expanded_nodes: &'a mut usize,
    expression_depth: usize,
    validate_ids: bool,
    declarations: &'a BTreeMap<Key<'model>, Declaration<'model>>,
    slots: &'a BTreeMap<Key<'model>, usize>,
    arithmetic_depths: &'a BTreeMap<Key<'model>, usize>,
    numeric_bounds: &'a BTreeMap<Key<'model>, NumericBounds>,
    limb_products: &'a mut usize,
    division_limb_visits: &'a mut usize,
    types: &'a Types<'model>,
    type_expansion: &'a mut usize,
    namespace: &'model str,
    functions: &'a mut Vec<(usize, Vec<String>, bool, String)>,
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
        if !self.types.extended
            && self.filter_predicate
            && matches!(expression.node, Some(Node::Context(_)))
        {
            return Err("contexts are unsupported in filter predicates");
        }
        *self.expanded_nodes += 1;
        let max_nodes = if self.types.extended { 200_000 } else { 50_000 };
        if *self.expanded_nodes > max_nodes {
            return Err("expanded expression budget exceeded");
        }
        self.expression_depth += 1;
        let max_depth = if self.types.extended { 128 } else { 32 };
        if self.expression_depth > max_depth {
            return Err("expanded expression depth exceeded");
        }
        let ty = self.types.expression(expression, self.namespace)?;
        self.types.charge(ty, self.type_expansion)?;
        let mut depth = 0;
        let mut bounds = None;
        let value = match expression.node.as_ref().ok_or("missing expression node")? {
            Node::Closure(closure) if self.structured => {
                self.emit_first_class_closure(closure)?
            }
            Node::ContextLiteral(ctx_lit) if self.structured => {
                let outer = self.locals.clone();
                let outer_closures = self.closures.clone();
                let mut block = String::from("{ let mut __ctx = BTreeMap::new(); ");
                for binding in &ctx_lit.entries {
                    let id = nonempty(&binding.binding_id)?;
                    let slot = {
                        *self.call_serial += 1;
                        *self.call_serial
                    };
                    if let Some(closure) = &binding.closure {
                        let def = closure
                            .definition
                            .as_ref()
                            .ok_or("missing closure definition")?;
                        let fn_val = self.emit_first_class_closure(def)?;
                        write!(block, "let l{slot} = {fn_val}; __ctx.insert({:?}.to_string(), l{slot}.clone()); ", closure.name).unwrap();
                        self.locals.insert(
                            id.to_owned(),
                            Emitted {
                                ty: Type::Scalar(DmnIrScalarType::ScalarTypeNull),
                                value: format!("l{slot}.clone()"),
                                depth: 0,
                                bounds: None,
                            },
                        );
                        continue;
                    }
                    let variable = binding.variable.as_ref().ok_or("missing local variable")?;
                    let mut val = self.expression(
                        binding.value.as_ref().ok_or("missing local value")?,
                    )?;
                    depth = depth.max(val.depth);
                    write!(
                        block,
                        "let l{slot} = {}; __ctx.insert({:?}.to_string(), l{slot}.clone()); ",
                        val.value, variable.name
                    )
                    .unwrap();
                    val.value = format!("l{slot}.clone()");
                    self.locals.insert(id.to_owned(), val);
                }
                self.locals = outer;
                self.closures = outer_closures;
                block.push_str("Value::Record(__ctx) }");
                block
            }
            Node::Range(range) if self.structured => {
                let has_lower = range.lower.is_some();
                let has_upper = range.upper.is_some();
                let lower = if let Some(l) = range.lower.as_deref() {
                    let e = self.expression(l)?;
                    depth = depth.max(e.depth);
                    e.value
                } else {
                    "Value::Null".into()
                };
                let upper = if let Some(u) = range.upper.as_deref() {
                    let e = self.expression(u)?;
                    depth = depth.max(e.depth);
                    e.value
                } else {
                    "Value::Null".into()
                };
                format!(
                    "{{ let __lo = {lower}; let __hi = {upper}; if ({has_lower} && matches!(__lo, Value::Null)) || ({has_upper} && matches!(__hi, Value::Null)) {{ Value::Null }} else {{ Value::Range {{ lower: Box::new(__lo), upper: Box::new(__hi), lower_inclusive: {}, upper_inclusive: {} }} }} }}",
                    range.lower_inclusive, range.upper_inclusive
                )
            }
            Node::InstanceOf(inst) if self.structured => {
                let expr = self.expression(
                    inst.expression
                        .as_deref()
                        .ok_or("missing instance_of expression")?,
                )?;
                depth = expr.depth;
                let desc = inst
                    .tested_type
                    .as_ref()
                    .ok_or("missing instance_of type")?;
                let desc_src = emit_type_descriptor(desc);
                format!("eval_instance_of(&{}, &{})", expr.value, desc_src)
            }
            Node::ForExpression(for_expr) if self.structured => {
                self.emit_for_expression(for_expr)?
            }
            Node::Quantified(quant) if self.structured => {
                self.emit_quantified_expression(quant)?
            }
            Node::DynamicInvocation(dyn_inv) if self.structured => {
                let target = self.expression(
                    dyn_inv
                        .target
                        .as_deref()
                        .ok_or("missing invocation target")?,
                )?;
                depth = target.depth;
                let mut pos = Vec::new();
                for arg in &dyn_inv.positional_arguments {
                    let e = self.expression(arg)?;
                    depth = depth.max(e.depth);
                    pos.push(e.value);
                }
                let mut named = Vec::new();
                for arg in &dyn_inv.named_arguments {
                    let e = self.expression(
                        arg.value
                            .as_ref()
                            .ok_or("missing named argument value")?,
                    )?;
                    depth = depth.max(e.depth);
                    named.push(format!("({:?}, {})", arg.name, e.value));
                }
                format!(
                    "call_dynamic({}, &[{}], &[{}], &mut filter_budget)?",
                    target.value,
                    pos.join(", "),
                    named.join(", ")
                )
            }
            Node::ClosureInvocation(call) if self.closures_enabled => {
                let result = self.closure_call(call, ty)?;
                depth = result.depth;
                bounds = result.bounds;
                result.value
            }
            Node::BkmCall(call) if self.bkms_enabled => {
                let result = self.bkm_call(call, ty)?;
                depth = result.depth;
                bounds = result.bounds;
                result.value
            }
            Node::BuiltinCall(call) if self.builtins_enabled && self.types.extended => {
                if !(1..=85).contains(&call.operation) {
                    return Err("unsupported builtin operation");
                }
                if matches!(call.operation, 1 | 2 | 3) {
                    let ok_res = match call.operation {
                        1 => {
                            ty == Number
                                || ty == DmnIrScalarType::ScalarTypeDuration
                                || ty == DmnIrScalarType::ScalarTypeNull
                        }
                        2 => ty == Number || ty == DmnIrScalarType::ScalarTypeNull,
                        3 => ty == Boolean || ty == DmnIrScalarType::ScalarTypeNull,
                        _ => true,
                    };
                    if !ok_res {
                        return Err("builtin result type mismatch");
                    }
                    let expected_arity = if call.operation == 3 { 2 } else { 1 };
                    if call.arguments.len() != expected_arity {
                        return Err("builtin argument count mismatch");
                    }
                }
                let mut args = Vec::new();
                for arg in &call.arguments {
                    let emitted = self.expression(arg)?;
                    if call.operation == 1
                        && emitted.ty != Number
                        && emitted.ty != DmnIrScalarType::ScalarTypeDuration
                        && emitted.ty != DmnIrScalarType::ScalarTypeNull
                    {
                        return Err("builtin argument type mismatch");
                    }
                    if matches!(call.operation, 2 | 3)
                        && emitted.ty != Text
                        && emitted.ty != DmnIrScalarType::ScalarTypeNull
                    {
                        return Err("builtin argument type mismatch");
                    }
                    depth = depth.max(emitted.depth);
                    if call.operation == 1 {
                        bounds = emitted.bounds;
                    }
                    args.push(emitted.value);
                }
                if call.operation == 2 {
                    bounds = Some(NumericBounds::literal(&u64::MAX.to_string(), 0));
                }
                format!(
                    "eval_builtin({}, vec![{}], &mut filter_budget)?",
                    call.operation,
                    args.join(", ")
                )
            }
            Node::BuiltinCall(call) if self.builtins_enabled => {
                if call.operation == 23 {
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    match ty {
                        Type::Collection(idx) => {
                            if !assignable(self.types.collections[idx], Text) {
                                return Err("builtin result type mismatch");
                            }
                        }
                        _ => return Err("builtin result type mismatch"),
                    }
                    let emitted_input = self.expression(&call.arguments[0])?;
                    if !assignable(emitted_input.ty, Text) {
                        return Err("builtin argument type mismatch");
                    }
                    let emitted_delim = self.expression(&call.arguments[1])?;
                    if !assignable(emitted_delim.ty, Text) {
                        return Err("builtin argument type mismatch");
                    }
                    depth = depth.max(emitted_input.depth).max(emitted_delim.depth);
                    format!(
                        "builtin_split({},{})",
                        emitted_input.value, emitted_delim.value
                    )
                } else if call.operation == 37 {
                    if call.arguments.len() != 1 {
                        return Err("builtin argument count mismatch");
                    }
                    if ty != Text {
                        return Err("builtin result type mismatch");
                    }
                    let emitted = self.expression(&call.arguments[0])?;
                    depth = depth.max(emitted.depth);
                    format!("builtin_string({})", emitted.value)
                } else if call.operation == 24 {
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    if ty != Text {
                        return Err("builtin result type mismatch");
                    }
                    let emitted_list = self.expression(&call.arguments[0])?;
                    let valid_list = match emitted_list.ty {
                        Type::Collection(idx) => assignable(self.types.collections[idx], Text),
                        Type::Scalar(DmnIrScalarType::ScalarTypeString)
                        | Type::Scalar(DmnIrScalarType::ScalarTypeNull) => true,
                        _ => false,
                    };
                    if !valid_list {
                        return Err("builtin argument type mismatch");
                    }
                    let emitted_delim = self.expression(&call.arguments[1])?;
                    if !assignable(emitted_delim.ty, Text) {
                        return Err("builtin argument type mismatch");
                    }
                    depth = depth.max(emitted_list.depth).max(emitted_delim.depth);
                    format!(
                        "builtin_string_join({},{})",
                        emitted_list.value, emitted_delim.value
                    )
                } else if call.operation == 38 {
                    // sublist(list, start_pos, length?)
                    if call.arguments.len() < 2 || call.arguments.len() > 3 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted_list = self.expression(&call.arguments[0])?;
                    let emitted_start = self.expression(&call.arguments[1])?;
                    let emitted_len = if call.arguments.len() == 3 {
                        self.expression(&call.arguments[2])?
                    } else {
                        Emitted { value: "Value::Null".into(), ty: Type::Scalar(DmnIrScalarType::ScalarTypeNull), depth: 0, bounds: None }
                    };
                    depth = depth.max(emitted_list.depth).max(emitted_start.depth).max(emitted_len.depth);
                    format!("builtin_sublist({},{},{})", emitted_list.value, emitted_start.value, emitted_len.value)
                } else if call.operation == 39 {
                    // append(list, item)
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted_list = self.expression(&call.arguments[0])?;
                    let emitted_item = self.expression(&call.arguments[1])?;
                    depth = depth.max(emitted_list.depth).max(emitted_item.depth);
                    format!("builtin_append({},{})", emitted_list.value, emitted_item.value)
                } else if call.operation == 40 {
                    // concatenate(list1, list2)
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted1 = self.expression(&call.arguments[0])?;
                    let emitted2 = self.expression(&call.arguments[1])?;
                    depth = depth.max(emitted1.depth).max(emitted2.depth);
                    format!("builtin_concatenate({},{})", emitted1.value, emitted2.value)
                } else if call.operation == 41 {
                    // insert before(list, pos, item)
                    if call.arguments.len() != 3 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted_list = self.expression(&call.arguments[0])?;
                    let emitted_pos = self.expression(&call.arguments[1])?;
                    let emitted_item = self.expression(&call.arguments[2])?;
                    depth = depth.max(emitted_list.depth).max(emitted_pos.depth).max(emitted_item.depth);
                    format!("builtin_insert_before({},{},{})", emitted_list.value, emitted_pos.value, emitted_item.value)
                } else if call.operation == 42 {
                    // remove(list, pos)
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted_list = self.expression(&call.arguments[0])?;
                    let emitted_pos = self.expression(&call.arguments[1])?;
                    depth = depth.max(emitted_list.depth).max(emitted_pos.depth);
                    format!("builtin_remove({},{})", emitted_list.value, emitted_pos.value)
                } else if call.operation == 43 {
                    // reverse(list)
                    if call.arguments.len() != 1 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted = self.expression(&call.arguments[0])?;
                    depth = depth.max(emitted.depth);
                    format!("builtin_reverse({})", emitted.value)
                } else if call.operation == 44 {
                    // index of(list, match)
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted_list = self.expression(&call.arguments[0])?;
                    let emitted_match = self.expression(&call.arguments[1])?;
                    depth = depth.max(emitted_list.depth).max(emitted_match.depth);
                    format!("builtin_index_of({},{})", emitted_list.value, emitted_match.value)
                } else if call.operation == 45 {
                    // distinct values(list)
                    if call.arguments.len() != 1 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted = self.expression(&call.arguments[0])?;
                    depth = depth.max(emitted.depth);
                    format!("builtin_distinct_values({})", emitted.value)
                } else if call.operation == 46 {
                    // flatten(list)
                    if call.arguments.len() != 1 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted = self.expression(&call.arguments[0])?;
                    depth = depth.max(emitted.depth);
                    format!("builtin_flatten({})", emitted.value)
                } else if call.operation == 47 {
                    // union(list1, list2)
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted1 = self.expression(&call.arguments[0])?;
                    let emitted2 = self.expression(&call.arguments[1])?;
                    depth = depth.max(emitted1.depth).max(emitted2.depth);
                    format!("builtin_union({},{})", emitted1.value, emitted2.value)
                } else if call.operation == 48 {
                    // list contains(list, item)
                    if call.arguments.len() != 2 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted_list = self.expression(&call.arguments[0])?;
                    let emitted_item = self.expression(&call.arguments[1])?;
                    depth = depth.max(emitted_list.depth).max(emitted_item.depth);
                    format!("builtin_list_contains({},{})", emitted_list.value, emitted_item.value)
                } else if call.operation == 49 {
                    // count(list)
                    if call.arguments.len() != 1 {
                        return Err("builtin argument count mismatch");
                    }
                    let emitted = self.expression(&call.arguments[0])?;
                    depth = depth.max(emitted.depth);
                    format!("builtin_count({})", emitted.value)
                } else if (26..=32).contains(&call.operation) || call.operation == 34 {
                    if call.arguments.len() != 1 {
                        return Err("builtin argument count mismatch");
                    }
                    let (expected_elem, expected_res, function) = match call.operation {
                        26 => (Number, Number, "builtin_sum"),
                        27 => (Number, Number, "builtin_min"),
                        28 => (Number, Number, "builtin_max"),
                        29 => (Number, Number, "builtin_mean"),
                        30 => (Number, Number, "builtin_median"),
                        31 => (Boolean, Boolean, "builtin_all"),
                        32 => (Boolean, Boolean, "builtin_any"),
                        34 => (Number, Number, "builtin_product"),
                        _ => unreachable!(),
                    };
                    if ty != expected_res {
                        return Err("builtin result type mismatch");
                    }
                    let emitted = self.expression(&call.arguments[0])?;
                    let valid_arg = match emitted.ty {
                        Type::Collection(idx) => {
                            assignable(self.types.collections[idx], expected_elem)
                        }
                        Type::Scalar(DmnIrScalarType::ScalarTypeNull) => true,
                        _ => false,
                    };
                    if !valid_arg {
                        return Err("builtin argument type mismatch");
                    }
                    depth = depth.max(emitted.depth);
                    format!("{function}({})", emitted.value)
                } else {
                    let (parameters, result, function): (
                        &[DmnIrScalarType],
                        DmnIrScalarType,
                        &str,
                    ) = match call.operation {
                        1 => (&[Number], Number, "builtin_abs"),
                        2 => (&[Text], Number, "builtin_string_length"),
                        3 => (&[Text, Text], Boolean, "builtin_contains"),
                        4 => (&[Number, Number], Number, "builtin_modulo"),
                        5 => (&[Number, Number], Number, "builtin_floor"),
                        6 => (&[Number, Number], Number, "builtin_ceiling"),
                        7 => (&[Number], Boolean, "builtin_even"),
                        8 => (&[Number], Boolean, "builtin_odd"),
                        9 => (&[Number, Number], Number, "builtin_decimal"),
                        10 => (&[Number, Number], Number, "builtin_round_up"),
                        11 => (&[Number, Number], Number, "builtin_round_down"),
                        12 => (&[Number, Number], Number, "builtin_round_half_up"),
                        13 => (&[Number, Number], Number, "builtin_round_half_down"),
                        14 => (&[Text], Text, "builtin_upper_case"),
                        15 => (&[Text], Text, "builtin_lower_case"),
                        16 => (&[Text, Number, Number], Text, "builtin_substring"),
                        17 => (&[Text, Text], Text, "builtin_substring_before"),
                        18 => (&[Text, Text], Text, "builtin_substring_after"),
                        19 => (&[Text, Text], Boolean, "builtin_starts_with"),
                        20 => (&[Text, Text], Boolean, "builtin_ends_with"),
                        21 => (&[Text, Text, Text], Boolean, "builtin_matches"),
                        22 => (&[Text, Text, Text, Text], Text, "builtin_replace"),
                        25 => (&[Text, Text, Text], Number, "builtin_number_from_string"),
                        33 => (&[Number], Number, "builtin_sqrt"),
                        35 => (&[Number], Number, "builtin_exp"),
                        36 => (&[Number], Number, "builtin_log"),
                        _ => return Err("unsupported builtin operation"),
                    };
                    if ty != result {
                        return Err("builtin result type mismatch");
                    }
                    if call.arguments.len() != parameters.len() {
                        return Err("builtin argument count mismatch");
                    }
                    let mut arguments = Vec::new();
                    for (argument, parameter) in call.arguments.iter().zip(parameters) {
                        let emitted = self.expression(argument)?;
                        if !assignable(emitted.ty, *parameter) {
                            return Err("builtin argument type mismatch");
                        }
                        depth = depth.max(emitted.depth);
                        if call.operation == 1 {
                            bounds = emitted.bounds;
                        }
                        arguments.push(emitted.value);
                    }
                    if call.operation == 2 {
                        bounds = Some(NumericBounds::literal(&u64::MAX.to_string(), 0));
                    }
                    format!("{function}({})", arguments.join(","))
                }
            }
            Node::Filter(filter) if self.filters_enabled => {
                if !self.types.extended && self.filter_predicate {
                    return Err("filters inside predicates are unsupported");
                }
                let item_ty = match ty {
                    Type::Collection(index) => self.types.collections[index],
                    Type::Scalar(DmnIrScalarType::ScalarTypeNull) if self.types.extended => {
                        Type::Scalar(DmnIrScalarType::ScalarTypeNull)
                    }
                    _ => return Err("filter requires named collection type"),
                };
                let source =
                    self.expression(filter.source.as_deref().ok_or("missing filter source")?)?;
                if !self.types.extended && source.ty != ty {
                    return Err("filter source type mismatch");
                }
                let id = nonempty(&filter.item_binding_id)?;
                if !self.types.extended && !self.binding_ids.insert(id.to_owned()) {
                    return Err("duplicate filter binding ID");
                }
                let slot = {
                    *self.call_serial += 1;
                    *self.call_serial
                };
                let prev_local = self.locals.insert(
                    id.to_owned(),
                    Emitted {
                        ty: item_ty,
                        value: format!("f{slot}.clone()"),
                        depth: source.depth,
                        bounds: source.bounds,
                    },
                );
                let products_before = *self.limb_products;
                let divisions_before = *self.division_limb_visits;
                let prev_fp = self.filter_predicate;
                self.filter_predicate = true;
                let predicate = self.expression(
                    filter
                        .predicate
                        .as_deref()
                        .ok_or("missing filter predicate")?,
                )?;
                self.filter_predicate = prev_fp;
                if let Some(prev) = prev_local {
                    self.locals.insert(id.to_owned(), prev);
                } else {
                    self.locals.remove(id);
                }
                if !self.types.extended && predicate.ty != Boolean {
                    return Err("filter predicate must be boolean");
                }
                let products = *self.limb_products - products_before;
                let divisions = *self.division_limb_visits - divisions_before;
                depth = source.depth.max(predicate.depth);
                bounds = source.bounds;
                if self.types.extended {
                    format!(
                        "eval_filter({}, |f{slot}, mut filter_budget| Ok({}), {products}, {divisions}, &mut filter_budget)?",
                        source.value, predicate.value
                    )
                } else {
                    format!(
                        "match {} {{ Value::List(elements) => {{ filter_budget.charge(elements.len(), {products}, {divisions})?; {{ let mut retained = Vec::new(); for f{slot} in elements {{ let f{slot} = &f{slot}; if matches!({}, Value::Boolean(true)) {{ retained.push(f{slot}.clone()); }} }} Value::List(retained) }} }}, _ => Value::Null }}",
                        source.value, predicate.value
                    )
                }
            }
            Node::List(list) if self.collections_enabled => {
                let elem_ty = match ty {
                    Type::Collection(index) => Some(self.types.collections[index]),
                    Type::Scalar(DmnIrScalarType::ScalarTypeNull) if self.types.extended => None,
                    _ => return Err("list requires a named collection type"),
                };
                let mut elements = Vec::new();
                for element in &list.elements {
                    let emitted = self.expression(element)?;
                    if !self.types.extended {
                        let expected_elem = elem_ty.unwrap();
                        if !matches!(emitted.ty, Type::Scalar(_))
                            || !assignable(emitted.ty, expected_elem)
                        {
                            return Err("list element type mismatch");
                        }
                    }
                    depth = depth.max(emitted.depth);
                    bounds = match (bounds, emitted.bounds) {
                        (Some(a), Some(b)) => Some(a.join(b)),
                        (a, b) => a.or(b),
                    };
                    elements.push(emitted.value);
                }
                format!("Value::List(vec![{}])", elements.join(","))
            }
            Node::Literal(literal) => {
                if !self.types.extended && matches!(ty, Type::Collection(_)) {
                    return Err("collection literal requires a list constructor or scalar null");
                }
                let (source, literal_bounds) =
                    literal_source(literal, ty, self.dates, self.types.extended)?;
                bounds = literal_bounds;
                source
            }
            Node::GlobalReference(reference) => {
                let target = key(reference);
                if !self.dependencies.contains(&target) {
                    return Err("expression reference is not a declared dependency");
                }
                if !self.types.extended {
                    if if matches!(ty, Type::Collection(_)) {
                        self.declarations[&target].ty != ty
                    } else {
                        !assignable(self.declarations[&target].ty, ty)
                    } {
                        return Err("reference type mismatch");
                    }
                }
                depth = self.arithmetic_depths.get(&target).copied().unwrap_or(0);
                bounds = self.numeric_bounds.get(&target).copied();
                format!("v{}.clone()", self.slots[&target])
            }
            Node::LocalReference(reference) if self.locals_enabled => {
                if let Some(unused) = &mut self.unused_captures {
                    unused.remove(&reference.binding_id);
                }
                let local = self
                    .locals
                    .get(&reference.binding_id)
                    .ok_or("local binding is not visible")?;
                if !self.types.extended && local.ty != ty {
                    return Err("local reference type mismatch");
                }
                depth = local.depth;
                bounds = local.bounds;
                local.value.clone()
            }
            Node::Context(context) if self.locals_enabled => {
                let outer = self.locals.clone();
                let outer_closures = self.closures.clone();
                let mut block = String::from("{ ");
                let mut names = BTreeSet::new();
                for binding in &context.bindings {
                    let id = nonempty(&binding.binding_id)?;
                    let inserted = self.binding_ids.insert(id.to_owned());
                    if !self.types.extended && !inserted {
                        return Err("duplicate local binding ID");
                    }
                    if let Some(closure) = &binding.closure {
                        if !self.closures_enabled {
                            return Err("closures require profile 0.11");
                        }
                        if binding.variable.is_some() || binding.value.is_some() {
                            return Err("closure binding cannot contain a value variable");
                        }
                        if !names.insert(nonempty(&closure.name)?) {
                            return Err("duplicate context variable name");
                        }
                        if let Some(id) = &closure.id {
                            if self.validate_ids
                                && !self.source_ids.insert(nonempty(id)?.to_owned())
                            {
                                return Err("duplicate variable ID");
                            }
                        }
                        let def = closure
                            .definition
                            .as_ref()
                            .ok_or("missing closure definition")?;
                        let definition = self.validate_closure(def)?;
                        self.closures.insert(id.to_owned(), definition);
                        let slot = {
                            *self.call_serial += 1;
                            *self.call_serial
                        };
                        let fn_val = self.emit_first_class_closure(def)?;
                        write!(block, "let l{slot} = {fn_val}; ").unwrap();
                        self.locals.insert(
                            id.to_owned(),
                            Emitted {
                                ty: Type::Scalar(DmnIrScalarType::ScalarTypeNull),
                                value: format!("l{slot}.clone()"),
                                depth: 0,
                                bounds: None,
                            },
                        );
                        continue;
                    }
                    let variable = binding.variable.as_ref().ok_or("missing local variable")?;
                    if !names.insert(nonempty(&variable.name)?) {
                        return Err("duplicate context variable name");
                    }
                    if let Some(id) = &variable.id {
                        if self.validate_ids && !self.source_ids.insert(nonempty(id)?.to_owned()) {
                            return Err("duplicate variable ID");
                        }
                    }
                    let declared = self.types.variable(variable, self.namespace)?;
                    self.types.charge(declared, self.type_expansion)?;
                    let slot = {
                        *self.call_serial += 1;
                        *self.call_serial
                    };
                    let mut value =
                        self.expression(binding.value.as_ref().ok_or("missing local value")?)?;
                    if !self.types.extended && !assignable_ext(value.ty, declared, self.locals_enabled) {
                        return Err("local binding type mismatch");
                    }
                    depth = depth.max(value.depth);
                    if self.types.extended {
                        write!(
                            block,
                            "let l{slot} = coerce_output({}, {}); ",
                            value.value,
                            declared.source()
                        )
                        .unwrap();
                    } else {
                        write!(block, "let l{slot} = {}; ", value.value).unwrap();
                    }
                    value.value = format!("l{slot}.clone()");
                    value.ty = declared;
                    self.locals.insert(id.to_owned(), value);
                }
                let result =
                    self.expression(context.result.as_deref().ok_or("missing context result")?)?;
                if !assignable_ext(result.ty, ty, self.locals_enabled) {
                    return Err("context result type mismatch");
                }
                depth = depth.max(result.depth);
                bounds = result.bounds;
                write!(block, "{} }}", result.value).unwrap();
                self.locals = outer;
                self.closures = outer_closures;
                block
            }
            Node::FieldAccess(access) => {
                let source =
                    self.expression(access.source.as_deref().ok_or("missing field source")?)?;
                if !self.locals_enabled {
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
                }
                depth = source.depth;
                if ty == Number
                    || matches!(ty, Type::Collection(index) if self.types.collections[index] == Number)
                {
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
                if self.types.extended {
                    match DmnIrUnaryOperator::try_from(unary.operator) {
                        Ok(UnaryOperatorPositive) => {
                            bounds = operand_bounds;
                            format!("feel_pos({operand})")
                        }
                        Ok(UnaryOperatorNegate) => {
                            bounds = operand_bounds;
                            format!("feel_neg({operand})")
                        }
                        Ok(UnaryOperatorNot) => format!("not({operand})"),
                        _ => return Err("unsupported unary signature"),
                    }
                } else {
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
                if self.types.extended {
                    match DmnIrBinaryOperator::try_from(binary.operator) {
                        Ok(BinaryOperatorAdd) => {
                            if let Some((a, b)) = left_bounds.zip(right_bounds) {
                                bounds = Some(a.sum(b));
                            }
                            format!("feel_add({left}, {right})")
                        }
                        Ok(BinaryOperatorSubtract) => {
                            if let Some((a, b)) = left_bounds.zip(right_bounds) {
                                bounds = Some(a.sum(b));
                            }
                            format!("feel_sub({left}, {right})")
                        }
                        Ok(BinaryOperatorMultiply) => {
                            if let Some((a, b)) = left_bounds.zip(right_bounds) {
                                bounds = Some(a.product(b));
                            }
                            format!("feel_mul({left}, {right})")
                        }
                        Ok(BinaryOperatorDivide) => {
                            if let Some((a, b)) = left_bounds.zip(right_bounds) {
                                bounds = a.quotient(b);
                            }
                            format!("feel_div({left}, {right})")
                        }
                        Ok(BinaryOperatorPower) => {
                            if let Ok((coefficient, scale)) = crate::power::constant_power(
                                binary.left.as_deref().unwrap(),
                                binary.right.as_deref().unwrap(),
                            ) {
                                format!(
                                    "Value::Number {{ coefficient: {coefficient:?}.into(), scale: {scale} }}"
                                )
                            } else {
                                format!("power({left}, {right})")
                            }
                        }
                        Ok(BinaryOperatorEqual) => format!("equal({left}, {right})"),
                        Ok(BinaryOperatorNotEqual) => format!("not(equal({left}, {right}))"),
                        Ok(BinaryOperatorLess) => {
                            format!("ordered({left}, {right}, Ordering::is_lt)")
                        }
                        Ok(BinaryOperatorLessEqual) => {
                            format!("ordered({left}, {right}, Ordering::is_le)")
                        }
                        Ok(BinaryOperatorGreater) => {
                            format!("ordered({left}, {right}, Ordering::is_gt)")
                        }
                        Ok(BinaryOperatorGreaterEqual) => {
                            format!("ordered({left}, {right}, Ordering::is_ge)")
                        }
                        Ok(BinaryOperatorAnd) => format!("and({left}, || Ok({right}))?"),
                        Ok(BinaryOperatorOr) => format!("or({left}, || Ok({right}))?"),
                        _ => return Err("unsupported binary signature"),
                    }
                } else {
                    let logical =
                        matches!(left_type, Type::Scalar(_)) && matches!(right_type, Type::Scalar(_));
                    let null_ty = DmnIrScalarType::ScalarTypeNull;
                    let is_orderable = |t: Type| {
                        t == Number
                            || t == Text
                            || (self.dates && t == DmnIrScalarType::ScalarTypeDate)
                            || t == null_ty
                    };
                    let ordered = (left_type == right_type && is_orderable(left_type))
                        || (left_type == null_ty && is_orderable(right_type))
                        || (right_type == null_ty && is_orderable(left_type));
                    let is_null_arithmetic = (ty == Number || ty == null_ty)
                        && (left_type == Number || left_type == null_ty)
                        && (right_type == Number || right_type == null_ty);
                    let numeric = is_null_arithmetic;
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
                        Ok(BinaryOperatorAdd)
                            if ty == Text && left_type == Text && right_type == Text =>
                        {
                            format!("string_concat({left}, {right})")
                        }
                        Ok(BinaryOperatorPower) if numeric => {
                            if let Ok((coefficient, scale)) = crate::power::constant_power(
                                binary.left.as_deref().unwrap(),
                                binary.right.as_deref().unwrap(),
                            ) {
                                bounds = Some(NumericBounds::literal(&coefficient, scale));
                                format!(
                                    "Value::Number {{ coefficient: {coefficient:?}.into(), scale: {scale} }}"
                                )
                            } else {
                                format!("power({left}, {right})")
                            }
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
                        Ok(BinaryOperatorAnd) if logical => format!("and({left}, || Ok({right}))?"),
                        Ok(BinaryOperatorOr) if logical => format!("or({left}, || Ok({right}))?"),
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
                if !assignable_ext(condition_type, Boolean, self.locals_enabled)
                    || !assignable_ext(then_type, ty, self.locals_enabled)
                    || !assignable_ext(else_type, ty, self.locals_enabled)
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
            if !self.closures_enabled {
                bounds.verify()?;
            }
        }
        self.expression_depth -= 1;
        Ok(Emitted {
            ty,
            value,
            depth,
            bounds,
        })
    }

    fn emit_first_class_closure(&mut self, closure: &DmnIrClosure) -> Check<String> {
        let mut capture_exprs = Vec::new();
        let mut inner_locals = BTreeMap::new();
        let mut cap_idx = 0usize;
        // Capture all visible locals
        for (bid, emitted) in &self.locals {
            capture_exprs.push(emitted.value.clone());
            inner_locals.insert(
                bid.clone(),
                Emitted {
                    ty: emitted.ty,
                    value: format!("captures[{cap_idx}].clone()"),
                    depth: emitted.depth,
                    bounds: emitted.bounds,
                },
            );
            cap_idx += 1;
        }
        // Capture all declared dependencies (globals) into captures so the closure body can access them
        let mut global_cap_map: BTreeMap<Key<'_>, usize> = BTreeMap::new();
        for &dep in &self.dependencies {
            if let Some(&slot) = self.slots.get(&dep) {
                capture_exprs.push(format!("v{slot}.clone()"));
                global_cap_map.insert(dep, cap_idx);
                cap_idx += 1;
            }
        }
        let mut param_names = Vec::new();
        for (p_idx, param) in closure.parameters.iter().enumerate() {
            let pname = param
                .variable
                .as_ref()
                .map(|v| v.name.clone())
                .unwrap_or_default();
            param_names.push(pname);
            inner_locals.insert(
                param.binding_id.clone(),
                Emitted {
                    ty: Type::Scalar(DmnIrScalarType::ScalarTypeNull),
                    value: format!("args[{p_idx}].clone()"),
                    depth: 0,
                    bounds: None,
                },
            );
        }
        let outer_locals = std::mem::replace(&mut self.locals, inner_locals);
        let outer_closures = std::mem::take(&mut self.closures);
        let outer_in_closure = std::mem::replace(&mut self.in_closure, false);
        let outer_unused = self.unused_captures.take();
        let body_emitted = self.expression(
            closure
                .body
                .as_deref()
                .ok_or("missing closure body")?,
        )?;
        self.locals = outer_locals;
        self.closures = outer_closures;
        self.in_closure = outer_in_closure;
        self.unused_captures = outer_unused;

        // Replace any `v{slot}.clone()` referencing captured globals with `captures[cap_idx].clone()`
        let mut body_src = body_emitted.value;
        for (dep, c_idx) in global_cap_map {
            let slot = self.slots[&dep];
            let needle = format!("v{slot}.clone()");
            let replacement = format!("captures[{c_idx}].clone()");
            body_src = body_src.replace(&needle, &replacement);
        }

        let fn_id = self.functions.len();
        self.functions
            .push((fn_id, param_names, closure.external, body_src));
        Ok(format!(
            "Value::Function {{ id: {fn_id}, captures: vec![{}] }}",
            capture_exprs.join(", ")
        ))
    }

    fn emit_for_expression(&mut self, for_expr: &DmnIrForExpression) -> Check<String> {
        let outer = self.locals.clone();
        let serial = {
            *self.call_serial += 1;
            *self.call_serial
        };
        let mut code = format!(
            "{{ let mut partial_{serial}: Vec<Value> = Vec::new(); let mut for_ok_{serial} = true; "
        );
        if !for_expr.partial_binding_id.is_empty() {
            self.locals.insert(
                for_expr.partial_binding_id.clone(),
                Emitted {
                    ty: Type::Scalar(DmnIrScalarType::ScalarTypeNull),
                    value: format!("Value::List(partial_{serial}.clone())"),
                    depth: 0,
                    bounds: None,
                },
            );
        }
        let mut closing = String::new();
        for (i, iter) in for_expr.iterations.iter().enumerate() {
            let src = self.expression(
                iter.source
                    .as_ref()
                    .ok_or("missing for iteration source")?,
            )?;
            let var_name = format!("fi_{serial}_{i}");
            if let Some(end_expr) = &iter.range_end {
                let end = self.expression(end_expr)?;
                write!(
                    code,
                    "if let Some(__items_{serial}_{i}) = for_range_values({}, {}) {{ for {var_name} in __items_{serial}_{i} {{ ",
                    src.value, end.value
                )
                .unwrap();
            } else {
                write!(
                    code,
                    "if let Some(__items_{serial}_{i}) = for_iter_values({}) {{ for {var_name} in __items_{serial}_{i} {{ ",
                    src.value
                )
                .unwrap();
            }
            self.locals.insert(
                iter.binding_id.clone(),
                Emitted {
                    ty: Type::Scalar(DmnIrScalarType::ScalarTypeNull),
                    value: format!("{var_name}.clone()"),
                    depth: 0,
                    bounds: None,
                },
            );
            closing.push_str(&format!("}} }} else {{ for_ok_{serial} = false; }} "));
        }
        let body = self.expression(for_expr.body.as_deref().ok_or("missing for body")?)?;
        write!(code, "partial_{serial}.push({}); ", body.value).unwrap();
        code.push_str(&closing);
        write!(
            code,
            "if for_ok_{serial} {{ Value::List(partial_{serial}) }} else {{ Value::Null }} }}"
        )
        .unwrap();
        self.locals = outer;
        Ok(code)
    }

    fn emit_quantified_expression(&mut self, quant: &DmnIrQuantifiedExpression) -> Check<String> {
        let outer = self.locals.clone();
        let serial = {
            *self.call_serial += 1;
            *self.call_serial
        };
        let mut code = format!(
            "{{ let mut q_true_{serial} = false; let mut q_false_{serial} = false; let mut q_null_{serial} = false; let mut q_err_{serial} = false; "
        );
        let mut closing = String::new();
        for (i, iter) in quant.bindings.iter().enumerate() {
            let src = self.expression(
                iter.source
                    .as_ref()
                    .ok_or("missing quantified source")?,
            )?;
            let var_name = format!("qi_{serial}_{i}");
            if let Some(end_expr) = &iter.range_end {
                let end = self.expression(end_expr)?;
                write!(
                    code,
                    "if let Some(__qitems_{serial}_{i}) = for_range_values({}, {}) {{ for {var_name} in __qitems_{serial}_{i} {{ ",
                    src.value, end.value
                )
                .unwrap();
            } else {
                write!(
                    code,
                    "if let Some(__qitems_{serial}_{i}) = for_iter_values({}) {{ for {var_name} in __qitems_{serial}_{i} {{ ",
                    src.value
                )
                .unwrap();
            }
            self.locals.insert(
                iter.binding_id.clone(),
                Emitted {
                    ty: Type::Scalar(DmnIrScalarType::ScalarTypeNull),
                    value: format!("{var_name}.clone()"),
                    depth: 0,
                    bounds: None,
                },
            );
            closing.push_str(&format!("}} }} else {{ q_err_{serial} = true; }} "));
        }
        let sat = self.expression(
            quant
                .satisfies
                .as_deref()
                .ok_or("missing quantified satisfies")?,
        )?;
        write!(
            code,
            "match {} {{ Value::Boolean(true) => q_true_{serial} = true, Value::Boolean(false) => q_false_{serial} = true, Value::Null => q_null_{serial} = true, _ => q_err_{serial} = true }} ",
            sat.value
        )
        .unwrap();
        code.push_str(&closing);
        if quant.every {
            write!(
                code,
                "if q_err_{serial} {{ Value::Null }} else if q_false_{serial} {{ Value::Boolean(false) }} else if q_null_{serial} {{ Value::Null }} else {{ Value::Boolean(true) }} }}"
            )
            .unwrap();
        } else {
            write!(
                code,
                "if q_err_{serial} {{ Value::Null }} else if q_true_{serial} {{ Value::Boolean(true) }} else if q_null_{serial} {{ Value::Null }} else {{ Value::Boolean(false) }} }}"
            )
            .unwrap();
        }
        self.locals = outer;
        Ok(code)
    }
}

fn emit_type_descriptor(desc: &DmnIrTypeDescriptor) -> String {
    let elem = match desc.element_type.as_deref() {
        Some(e) => format!("Some(Box::new({}))", emit_type_descriptor(e)),
        None => "None".into(),
    };
    let fields: Vec<String> = desc
        .fields
        .iter()
        .map(|f| {
            let ft = f
                .r#type
                .as_ref()
                .map(emit_type_descriptor)
                .unwrap_or_else(|| "TypeDesc::any()".into());
            format!("({:?}, {ft})", f.name)
        })
        .collect();
    let params: Vec<String> = desc
        .parameter_types
        .iter()
        .map(emit_type_descriptor)
        .collect();
    let ret = match desc.return_type.as_deref() {
        Some(r) => format!("Some(Box::new({}))", emit_type_descriptor(r)),
        None => "None".into(),
    };
    format!(
        "TypeDesc {{ kind: {:?}, element_type: {elem}, fields: vec![{}], parameter_types: vec![{}], return_type: {ret} }}",
        desc.kind,
        fields.join(", "),
        params.join(", ")
    )
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
    assignable_ext(actual, expected, false)
}

pub(super) fn assignable_ext(
    actual: impl Into<Type>,
    expected: impl Into<Type>,
    extended: bool,
) -> bool {
    let actual = actual.into();
    let expected = expected.into();
    actual == expected
        || actual == DmnIrScalarType::ScalarTypeNull
        || (extended && expected == DmnIrScalarType::ScalarTypeNull)
}

fn literal_source(
    literal: &DmnIrLiteral,
    expected: Type,
    dates: bool,
    extended: bool,
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
        Value::TimeValue(value) if extended => {
            (ScalarTypeTime, format!("Value::Time({value:?}.into())"))
        }
        Value::DateTimeValue(value) if extended => {
            (ScalarTypeDateTime, format!("Value::DateTime({value:?}.into())"))
        }
        Value::DurationValue(value) if extended => {
            (ScalarTypeDuration, format!("Value::Duration({value:?}.into())"))
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
    if !assignable_ext(actual, expected, extended) {
        return Err("literal type mismatch");
    }
    Ok((source, bounds))
}
