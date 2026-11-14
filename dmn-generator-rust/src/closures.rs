use super::*;

#[derive(Clone)]
pub(super) struct Closure {
    definition: DmnIrClosure,
    captures: BTreeMap<String, Emitted>,
    parameters: Vec<Type>,
    ty: Type,
}
impl ExpressionEmitter<'_, '_> {
    pub(super) fn validate_closure(&mut self, definition: &DmnIrClosure) -> Check<Closure> {
        if self.in_closure {
            return Err("nested closure definitions are unsupported");
        }
        let ty = self.types.reference(
            definition
                .result_type
                .as_ref()
                .ok_or("missing closure result type")?,
            self.namespace,
        )?;
        self.types.charge(ty, self.type_expansion)?;
        let mut captures = BTreeMap::new();
        let mut previous: Option<&str> = None;
        for id in &definition.captured_binding_ids {
            if previous.is_some_and(|p| p.as_bytes() >= id.as_bytes()) {
                return Err("closure captures must be unique and UTF-8 sorted");
            }
            let value = self
                .locals
                .get(id)
                .ok_or("closure capture is not visible")?
                .clone();
            captures.insert(id.clone(), value);
            previous = Some(id);
        }
        let mut locals = captures.clone();
        let mut parameters = Vec::new();
        let mut names = BTreeSet::new();
        for parameter in &definition.parameters {
            let id = nonempty(&parameter.binding_id)?;
            if !self.binding_ids.insert(id.to_owned()) {
                return Err("duplicate closure parameter binding ID");
            }
            let variable = parameter
                .variable
                .as_ref()
                .ok_or("missing closure parameter variable")?;
            if !names.insert(nonempty(&variable.name)?) {
                return Err("duplicate closure parameter name");
            }
            if let Some(id) = &variable.id {
                if self.validate_ids && !self.source_ids.insert(nonempty(id)?.to_owned()) {
                    return Err("duplicate variable ID");
                }
            }
            let ty = self.types.variable(variable, self.namespace)?;
            self.types.charge(ty, self.type_expansion)?;
            locals.insert(
                id.to_owned(),
                Emitted {
                    ty,
                    value: "Value::Null".into(),
                    depth: 0,
                    bounds: bkms::input_bounds(ty, self.types),
                },
            );
            parameters.push(ty);
        }
        let outer_locals = std::mem::replace(&mut self.locals, locals);
        let outer_closures = std::mem::take(&mut self.closures);
        let outer_dependencies = std::mem::take(&mut self.dependencies);
        self.in_closure = true;
        self.unused_captures = Some(captures.keys().cloned().collect());
        let products = *self.limb_products;
        let divisions = *self.division_limb_visits;
        *self.limb_products = 0;
        *self.division_limb_visits = 0;
        let result = self.expression(definition.body.as_deref().ok_or("missing closure body")?)?;
        if !assignable(result.ty, ty) {
            return Err("closure body type mismatch");
        }
        if !self.unused_captures.as_ref().unwrap().is_empty() {
            return Err("closure has unused captures");
        }
        *self.limb_products = products;
        *self.division_limb_visits = divisions;
        self.locals = outer_locals;
        self.closures = outer_closures;
        self.dependencies = outer_dependencies;
        self.in_closure = false;
        self.unused_captures = None;
        Ok(Closure {
            definition: definition.clone(),
            captures,
            parameters,
            ty,
        })
    }
    pub(super) fn closure_call(
        &mut self,
        call: &DmnIrClosureInvocation,
        ty: Type,
    ) -> Check<Emitted> {
        if self.in_closure {
            return Err("nested closure calls are unsupported");
        }
        use dmn_ir_closure_invocation::Target;
        let closure = match call.target.as_ref().ok_or("missing closure target")? {
            Target::LocalBindingId(id) => self
                .closures
                .get(id)
                .cloned()
                .ok_or("closure binding is not visible")?,
            Target::Inline(definition) => self.validate_closure(definition)?,
        };
        if closure.ty != ty {
            return Err("closure call result type mismatch");
        }
        if call.arguments.len() != closure.parameters.len() {
            return Err("closure argument count mismatch");
        }
        *self.call_serial += 1;
        let serial = *self.call_serial;
        let mut prefix = String::from("{ ");
        let mut locals = BTreeMap::new();
        let mut depth = 0;
        for (index, (id, captured)) in closure.captures.iter().enumerate() {
            write!(prefix, "let c{serial}_{index} = {}; ", captured.value).unwrap();
            let mut value = captured.clone();
            value.value = format!("c{serial}_{index}.clone()");
            locals.insert(id.clone(), value);
        }
        for (index, (argument, expected)) in
            call.arguments.iter().zip(&closure.parameters).enumerate()
        {
            let mut value = self.expression(argument)?;
            if !assignable(value.ty, *expected) {
                return Err("closure argument type mismatch");
            }
            write!(prefix, "let a{serial}_{index} = {}; ", value.value).unwrap();
            depth = depth.max(value.depth);
            value.ty = *expected;
            value.value = format!("a{serial}_{index}.clone()");
            locals.insert(
                closure.definition.parameters[index].binding_id.clone(),
                value,
            );
        }
        let outer_locals = std::mem::replace(&mut self.locals, locals);
        let outer_bindings =
            std::mem::replace(&mut self.binding_ids, self.locals.keys().cloned().collect());
        let outer_closures = std::mem::take(&mut self.closures);
        let outer_dependencies = std::mem::take(&mut self.dependencies);
        let outer_ids = std::mem::replace(&mut self.validate_ids, false);
        let outer_table = std::mem::replace(&mut self.table_cell, false);
        let outer_filter = std::mem::replace(&mut self.filter_predicate, false);
        self.in_closure = true;
        let mut result = self.expression(
            closure
                .definition
                .body
                .as_deref()
                .ok_or("missing closure body")?,
        )?;
        self.locals = outer_locals;
        self.binding_ids = outer_bindings;
        self.closures = outer_closures;
        self.dependencies = outer_dependencies;
        self.validate_ids = outer_ids;
        self.table_cell = outer_table;
        self.filter_predicate = outer_filter;
        self.in_closure = false;
        if !assignable(result.ty, ty) {
            return Err("closure body type mismatch");
        }
        write!(prefix, "{} }}", result.value).unwrap();
        result.value = prefix;
        result.ty = ty;
        result.depth = result.depth.max(depth);
        Ok(result)
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use prost::Message;
    fn fixture() -> DmnIrArtifact {
        DmnIrArtifact::decode(include_bytes!("../tests/fixtures/closures-0.11.dmnir").as_slice())
            .unwrap()
    }
    fn context(a: &mut DmnIrArtifact) -> &mut DmnIrContextExpression {
        let m = a
            .models
            .iter_mut()
            .find(|m| m.namespace == "urn:collections:policy")
            .unwrap();
        let e = m
            .decisions
            .iter_mut()
            .find(|d| d.identity.as_ref().unwrap().id == "echo")
            .unwrap()
            .expression
            .as_mut()
            .unwrap();
        let Some(Node::Context(c)) = &mut e.node else {
            panic!()
        };
        c
    }
    #[test]
    fn rejects_invalid_captures_definitions_and_bindings() {
        for (edit, expected) in [
            (
                (|d: &mut DmnIrClosure| d.captured_binding_ids = vec!["missing".into()])
                    as fn(&mut DmnIrClosure),
                "closure capture is not visible",
            ),
            (
                |d| {
                    d.captured_binding_ids
                        .push(d.captured_binding_ids[0].clone())
                },
                "closure captures must be unique and UTF-8 sorted",
            ),
            (|d| d.body = None, "missing closure body"),
            (|d| d.result_type = None, "missing closure result type"),
            (
                |d| d.parameters[0].binding_id = "closure-captured".into(),
                "duplicate closure parameter binding ID",
            ),
            (
                |d| {
                    d.body = Some(Box::new(DmnIrExpression {
                        r#type: 1,
                        node: Some(Node::Literal(DmnIrLiteral {
                            value: Some(Value::NullValue(0)),
                        })),
                        ..Default::default()
                    }))
                },
                "closure has unused captures",
            ),
            (
                |d| {
                    d.body.as_mut().unwrap().node = Some(Node::ClosureInvocation(Box::new(
                        DmnIrClosureInvocation::default(),
                    )))
                },
                "nested closure calls are unsupported",
            ),
            (
                |d| {
                    d.body.as_mut().unwrap().node = Some(Node::GlobalReference(DmnIrReference {
                        model_namespace: "urn:collections:policy".into(),
                        element_id: "external".into(),
                    }))
                },
                "expression reference is not a declared dependency",
            ),
        ] {
            let mut a = fixture();
            edit(
                context(&mut a).bindings[1]
                    .closure
                    .as_mut()
                    .unwrap()
                    .definition
                    .as_mut()
                    .unwrap(),
            );
            assert_eq!(verify_and_generate(&a), Err(expected));
        }
        let mut a = fixture();
        context(&mut a).bindings[1].variable = Some(DmnIrVariable::default());
        assert_eq!(
            verify_and_generate(&a),
            Err("closure binding cannot contain a value variable")
        );
        let mut a = fixture();
        a.schema_version.as_mut().unwrap().minor = Some(10);
        a.semantics_version = "finmsg-dmnir-named-feel-bkms/0".into();
        a.required_capabilities.pop();
        assert_eq!(
            verify_and_generate(&a),
            Err("closures require profile 0.11")
        );
    }
    #[test]
    fn rejects_invalid_closure_calls() {
        for (edit, expected) in [
            (
                (|c: &mut DmnIrClosureInvocation| c.arguments.clear())
                    as fn(&mut DmnIrClosureInvocation),
                "closure argument count mismatch",
            ),
            (
                |c| {
                    c.target = Some(dmn_ir_closure_invocation::Target::LocalBindingId(
                        "hidden".into(),
                    ))
                },
                "closure binding is not visible",
            ),
            (|c| c.target = None, "missing closure target"),
            (
                |c| {
                    c.arguments[0] = DmnIrExpression {
                        r#type: 4,
                        node: Some(Node::Literal(DmnIrLiteral {
                            value: Some(Value::StringValue("wrong".into())),
                        })),
                        ..Default::default()
                    }
                },
                "closure argument type mismatch",
            ),
        ] {
            let mut a = fixture();
            let Some(Node::Context(inner)) =
                &mut context(&mut a).bindings[2].value.as_mut().unwrap().node
            else {
                panic!()
            };
            let Some(Node::ClosureInvocation(call)) = &mut inner.result.as_mut().unwrap().node
            else {
                panic!()
            };
            edit(call);
            assert_eq!(verify_and_generate(&a), Err(expected));
        }
    }
}
