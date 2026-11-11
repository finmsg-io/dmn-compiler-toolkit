use super::*;

pub(super) fn generate(types: &Types<'_>, enabled: bool) -> Check<String> {
    let mut source = String::from(
        "fn field_constraint(index: usize, name: &str, candidate: &Value) -> bool { let t0 = candidate.clone(); match (index, name) {\n",
    );
    for (index, record) in types.records.iter().enumerate() {
        for ((name, ty), group) in record.fields.iter().zip(&record.constraints) {
            let Some(group) = group else { continue };
            if !enabled {
                return Err("field constraints require profile 0.6");
            }
            let Type::Scalar(scalar) = ty else {
                return Err("constraint requires non-null scalar field");
            };
            if *scalar == DmnIrScalarType::ScalarTypeNull {
                return Err("constraint requires non-null scalar field");
            }
            let expression = tests(group, *ty)?;
            writeln!(source, "({index}, {name:?}) => {expression},").unwrap();
        }
    }
    if !enabled {
        return Ok(String::new());
    }
    source.push_str("_ => true, } }\n");
    source.push_str(r#"
fn check_constraints(value: &Value, ty: Type) -> Result<(), &'static str> {
    if let (Value::Record(fields), Type::Record(index)) = (value, ty) {
        for &(name, field_type) in record_fields(index) {
            let candidate = fields.get(name).unwrap_or(&Value::Null);
            if !field_constraint(index, name, candidate) { return Err("field constraint violated"); }
            check_constraints(candidate, field_type)?;
        }
    }
    Ok(())
}
"#);
    Ok(source)
}
fn endpoint(value: Option<&DmnIrExpression>, ty: Type, ordered: bool) -> Check<String> {
    let value = value.ok_or("missing constraint endpoint")?;
    let Some(Node::Literal(literal)) = &value.node else {
        return Err("constraint endpoint must be constant");
    };
    let actual = Type::Scalar(crate::types::scalar_type(value.r#type, true)?);
    if value.record_type.is_some()
        || value.collection_type.is_some()
        || !assignable(actual, ty)
        || ordered && actual != ty
        || matches!(literal.value, Some(Value::NullValue(_)))
            && actual != DmnIrScalarType::ScalarTypeNull
    {
        return Err("constraint endpoint type mismatch");
    }
    Ok(literal_source(literal, actual, true)?.0)
}
fn tests(group: &DmnIrUnaryTests, ty: Type) -> Check<String> {
    if group.wildcard {
        return if group.negated || !group.tests.is_empty() {
            Err("invalid wildcard test")
        } else {
            Ok("true".into())
        };
    }
    if group.tests.is_empty() {
        return Err("empty unary tests");
    }
    let ordered = ty == DmnIrScalarType::ScalarTypeNumber
        || ty == DmnIrScalarType::ScalarTypeString
        || ty == DmnIrScalarType::ScalarTypeDate;
    let mut alternatives = Vec::new();
    for test in &group.tests {
        use dmn_ir_unary_test::Test;
        alternatives.push(match test.test.as_ref().ok_or("missing unary test")? {
            Test::Comparison(c) => {
                if !(6..=11).contains(&c.operator) {
                    return Err("unsupported test comparison");
                }
                if c.operator >= 8 && !ordered {
                    return Err("ordered test type mismatch");
                }
                let value = endpoint(c.endpoint.as_ref(), ty, c.operator >= 8)?;
                format!("table_compare(&t0, {value}, {})", c.operator)
            }
            Test::Range(r) => {
                if !ordered {
                    return Err("ordered test type mismatch");
                }
                let lower = endpoint(r.lower.as_ref(), ty, true)?;
                let upper = endpoint(r.upper.as_ref(), ty, true)?;
                format!(
                    "table_range(&t0, {lower}, {upper}, {}, {})",
                    r.lower_inclusive, r.upper_inclusive
                )
            }
        });
    }
    let union = format!("({})", alternatives.join(" || "));
    Ok(if group.negated {
        format!("!{union}")
    } else {
        union
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    use prost::Message;
    #[test]
    fn rejects_invalid_constraint_declarations() {
        let valid = DmnIrArtifact::decode(
            include_bytes!("../tests/fixtures/constraints-0.6.dmnir").as_slice(),
        )
        .unwrap();
        for (edit, expected) in [
            (
                (|f: &mut DmnIrRecordField| {
                    f.constraint = Some(DmnIrUnaryTests::default());
                }) as fn(&mut DmnIrRecordField),
                "empty unary tests",
            ),
            (
                |f| {
                    f.constraint = Some(DmnIrUnaryTests {
                        wildcard: true,
                        negated: true,
                        tests: vec![],
                    });
                },
                "invalid wildcard test",
            ),
            (
                |f| {
                    f.r#type = Some(DmnIrTypeReference {
                        kind: Some(dmn_ir_type_reference::Kind::ScalarType(1)),
                    });
                },
                "constraint requires non-null scalar field",
            ),
            (
                |f| {
                    let Some(dmn_ir_unary_test::Test::Range(range)) =
                        &mut f.constraint.as_mut().unwrap().tests[0].test
                    else {
                        panic!()
                    };
                    range.lower.as_mut().unwrap().node =
                        Some(Node::GlobalReference(DmnIrReference {
                            model_namespace: "urn:rust-proof:tables".into(),
                            element_id: "input".into(),
                        }));
                },
                "constraint endpoint must be constant",
            ),
            (
                |f| {
                    let Some(dmn_ir_unary_test::Test::Range(range)) =
                        &mut f.constraint.as_mut().unwrap().tests[0].test
                    else {
                        panic!()
                    };
                    range.lower.as_mut().unwrap().r#type = 4;
                },
                "constraint endpoint type mismatch",
            ),
        ] {
            let mut artifact = valid.clone();
            edit(&mut artifact.models[0].record_definitions[0].fields[0]);
            assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
            assert_eq!(verify_and_generate(&artifact), Err(expected));
        }
        let mut old = valid;
        old.schema_version.as_mut().unwrap().minor = Some(5);
        old.semantics_version = "finmsg-dmnir-tables/0".into();
        old.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&old),
            Err("field constraints require profile 0.6")
        );
    }
}
