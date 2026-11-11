use super::*;

impl ExpressionEmitter<'_, '_> {
    fn table_id(&mut self, id: &Option<String>) -> Check<()> {
        if let Some(id) = id {
            if !self.source_ids.insert(nonempty(id)?.to_owned()) {
                return Err("duplicate table ID");
            }
        }
        Ok(())
    }

    fn cell(
        &mut self,
        expression: Option<&DmnIrExpression>,
        ty: Type,
        cells: &mut Vec<Emitted>,
    ) -> Check<String> {
        let expression = expression.ok_or("missing table cell")?;
        let emitted = self.expression(expression)?;
        if !matches!(emitted.ty, Type::Scalar(_)) || !assignable(emitted.ty, ty) {
            return Err("table cell type mismatch");
        }
        let value = emitted.value.clone();
        cells.push(emitted);
        Ok(value)
    }

    pub(super) fn table(&mut self, table: &DmnIrDecisionTable, ty: Type) -> Check<Emitted> {
        if !(1..=3).contains(&table.hit_policy) {
            return Err("unsupported table hit policy");
        }
        if table.inputs.is_empty() || table.outputs.is_empty() || table.rules.is_empty() {
            return Err("table requires inputs outputs and rules");
        }
        self.table_id(&table.id)?;
        self.table_cell = true;
        let mut cells = Vec::new();
        let mut source = String::from("{ ");
        for (index, input) in table.inputs.iter().enumerate() {
            self.table_id(&input.id)?;
            let input_type = Type::Scalar(crate::types::scalar_type(input.r#type, true)?);
            let value = self.cell(input.expression.as_ref(), input_type, &mut cells)?;
            write!(source, "let t{index} = {value}; ").unwrap();
        }
        let fields = match ty {
            Type::Scalar(_) if table.outputs.len() == 1 => Vec::new(),
            Type::Record(index)
                if table.outputs.len() > 1
                    && self.types.records[index].fields.len() == table.outputs.len() =>
            {
                self.types.records[index].fields.clone()
            }
            _ => return Err("table result shape mismatch"),
        };
        let mut defaults = Vec::new();
        let mut output_cells = Vec::new();
        for (index, output) in table.outputs.iter().enumerate() {
            self.table_id(&output.id)?;
            if let Some(name) = &output.name {
                nonempty(name)?;
            }
            let output_type = Type::Scalar(crate::types::scalar_type(output.r#type, true)?);
            if fields.is_empty() {
                if output_type != ty {
                    return Err("table output type mismatch");
                }
            } else if output.name.as_deref() != Some(fields[index].0)
                || output_type != fields[index].1
            {
                return Err("table output fields mismatch");
            }
            defaults.push(if output.default_expression.is_some() {
                self.cell(
                    output.default_expression.as_ref(),
                    output_type,
                    &mut output_cells,
                )?
            } else {
                "Value::Null".into()
            });
        }
        source.push_str("let mut rows: Vec<Vec<Value>> = Vec::new(); ");
        for rule in &table.rules {
            self.table_id(&rule.id)?;
            if rule.input_entries.len() != table.inputs.len()
                || rule.output_entries.len() != table.outputs.len()
            {
                return Err("table rule column count mismatch");
            }
            let mut tests = Vec::new();
            for (index, group) in rule.input_entries.iter().enumerate() {
                tests.push(self.tests(group, table.inputs[index].r#type, index, &mut cells)?);
            }
            let mut values = Vec::new();
            for (entry, output) in rule.output_entries.iter().zip(&table.outputs) {
                values.push(self.cell(
                    Some(entry),
                    Type::Scalar(crate::types::scalar_type(output.r#type, true)?),
                    &mut output_cells,
                )?);
            }
            // Bitwise boolean conjunction evaluates all columns like the Java adapter.
            write!(
                source,
                "if {} {{ rows.push(vec![{}]); }} ",
                tests.join(" & "),
                values.join(",")
            )
            .unwrap();
        }
        let names = table
            .outputs
            .iter()
            .map(|o| format!("{:?}", o.name.as_deref().unwrap_or("")))
            .collect::<Vec<_>>()
            .join(",");
        write!(
            source,
            "table_result(rows, {}, &[{names}], || vec![{}])? }}",
            table.hit_policy,
            defaults.join(",")
        )
        .unwrap();
        self.table_cell = false;
        cells.extend(output_cells.iter().cloned());
        let depth = cells.iter().map(|c| c.depth).max().unwrap_or(0);
        let bounds = if ty == DmnIrScalarType::ScalarTypeNumber {
            output_cells
                .iter()
                .filter_map(|c| c.bounds)
                .reduce(|a, b| a.join(b))
        } else {
            None
        };
        Ok(Emitted {
            ty,
            value: source,
            depth,
            bounds,
        })
    }

    fn tests(
        &mut self,
        group: &DmnIrUnaryTests,
        scalar: i32,
        index: usize,
        cells: &mut Vec<Emitted>,
    ) -> Check<String> {
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
        let mut alternatives = Vec::new();
        let ty = Type::Scalar(crate::types::scalar_type(scalar, true)?);
        let ordered = matches!(scalar, 3 | 4 | 5);
        for test in &group.tests {
            use dmn_ir_unary_test::Test;
            let value = match test.test.as_ref().ok_or("missing unary test")? {
                Test::Comparison(comparison) => {
                    let op = comparison.operator;
                    if !(6..=11).contains(&op) {
                        return Err("unsupported test comparison");
                    }
                    let endpoint = comparison
                        .endpoint
                        .as_ref()
                        .ok_or("missing test endpoint")?;
                    if op >= 8 && (!ordered || endpoint.r#type != scalar) {
                        return Err("ordered test type mismatch");
                    }
                    if op <= 7
                        && endpoint.r#type == 1
                        && !matches!(
                            &endpoint.node,
                            Some(Node::Literal(DmnIrLiteral {
                                value: Some(Value::NullValue(0))
                            }))
                        )
                    {
                        return Err("null test endpoint must be explicit");
                    }
                    let value = self.cell(Some(endpoint), ty, cells)?;
                    format!("table_compare(&t{index}, {value}, {op})")
                }
                Test::Range(range) => {
                    let lower = range.lower.as_ref().ok_or("missing range lower")?;
                    let upper = range.upper.as_ref().ok_or("missing range upper")?;
                    if !ordered || lower.r#type != scalar || upper.r#type != scalar {
                        return Err("ordered test type mismatch");
                    }
                    let lower = self.cell(Some(lower), ty, cells)?;
                    let upper = self.cell(Some(upper), ty, cells)?;
                    format!(
                        "table_range(&t{index}, {lower}, {upper}, {}, {})",
                        range.lower_inclusive, range.upper_inclusive
                    )
                }
            };
            alternatives.push(value);
        }
        let union = format!("({})", alternatives.join(" || "));
        Ok(if group.negated {
            format!("!{union}")
        } else {
            union
        })
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use prost::Message;

    #[test]
    fn rejects_malformed_tables_and_inactive_cells() {
        let valid =
            DmnIrArtifact::decode(include_bytes!("../tests/fixtures/tables-0.5.dmnir").as_slice())
                .unwrap();
        for (edit, expected) in [
            (
                (|t: &mut DmnIrDecisionTable| t.hit_policy = 0) as fn(&mut DmnIrDecisionTable),
                "unsupported table hit policy",
            ),
            (
                |t| t.inputs.clear(),
                "table requires inputs outputs and rules",
            ),
            (
                |t| t.rules[0].input_entries.clear(),
                "table rule column count mismatch",
            ),
            (
                |t| t.rules[0].output_entries.clear(),
                "table rule column count mismatch",
            ),
            (
                |t| {
                    t.rules[0].input_entries[0] = DmnIrUnaryTests {
                        wildcard: true,
                        negated: true,
                        tests: vec![],
                    }
                },
                "invalid wildcard test",
            ),
            (
                |t| t.rules[0].input_entries[0] = DmnIrUnaryTests::default(),
                "empty unary tests",
            ),
            (|t| t.inputs[0].expression = None, "missing table cell"),
            (|t| t.id = Some("tables".into()), "duplicate table ID"),
            (
                |t| t.outputs[0].name = Some(" ".into()),
                "blank declaration or reference",
            ),
            (|t| t.outputs[0].r#type = 4, "table output type mismatch"),
            (
                |t| t.rules[1].output_entries[0].r#type = 4,
                "literal type mismatch",
            ),
            (
                |t| {
                    t.outputs[0].default_expression.as_mut().unwrap().node =
                        Some(Node::Context(Box::new(DmnIrContextExpression::default())))
                },
                "contexts are unsupported in table cells",
            ),
            (
                |t| {
                    let endpoint = t.rules[0].output_entries[0].clone();
                    t.rules[1].output_entries[0].node =
                        Some(Node::Binary(Box::new(DmnIrBinaryExpression {
                            operator: 5,
                            left: Some(Box::new(endpoint.clone())),
                            right: Some(Box::new(endpoint)),
                        })));
                },
                "unsupported binary signature",
            ),
        ] {
            let mut artifact = valid.clone();
            let decision = artifact.models[0]
                .decisions
                .iter_mut()
                .find(|d| d.identity.as_ref().unwrap().id == "first")
                .unwrap();
            edit(decision.decision_table.as_mut().unwrap());
            assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
            assert_eq!(verify_and_generate(&artifact), Err(expected));
        }
        let mut both = valid.clone();
        let decision = both.models[0]
            .decisions
            .iter_mut()
            .find(|d| d.identity.as_ref().unwrap().id == "first")
            .unwrap();
        decision.expression =
            Some(decision.decision_table.as_ref().unwrap().rules[0].output_entries[0].clone());
        assert_eq!(
            verify_and_generate(&both),
            Err("decision requires exactly one logic body")
        );
        let mut old = valid;
        old.schema_version.as_mut().unwrap().minor = Some(4);
        old.semantics_version = "finmsg-dmnir-dates/0".into();
        old.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&old),
            Err("decision tables require profile 0.5")
        );
    }
}
