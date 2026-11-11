use super::*;

pub(super) struct Bkm<'m> {
    pub source: &'m DmnIrBkm,
    pub ty: Type,
    pub parameters: Vec<Type>,
}

pub(super) fn register<'m>(
    model: &'m DmnIrModel,
    types: &Types<'m>,
    enabled: bool,
    ids: &mut BTreeSet<&'m str>,
    names: &mut BTreeSet<&'m String>,
    variable_names: &mut BTreeSet<&'m str>,
    expansion: &mut usize,
    registry: &mut BTreeMap<Key<'m>, Bkm<'m>>,
) -> Check<()> {
    if !enabled && !model.bkms.is_empty() {
        return Err("BKMs require profile 0.10");
    }
    for bkm in &model.bkms {
        let identity = bkm.identity.as_ref().ok_or("missing BKM identity")?;
        identity_fields(identity, ids)?;
        let variable = bkm.variable.as_ref().ok_or("missing BKM variable")?;
        if !names.insert(&identity.name) || !variable_names.insert(nonempty(&variable.name)?) {
            return Err("duplicate declaration name");
        }
        if let Some(id) = &variable.id {
            if !ids.insert(nonempty(id)?) {
                return Err("duplicate variable ID");
            }
        }
        let ty = types.variable(variable, &model.namespace)?;
        types.charge(ty, expansion)?;
        let mut bindings = BTreeSet::new();
        let mut parameter_names = BTreeSet::new();
        let mut parameters = Vec::new();
        for parameter in &bkm.parameters {
            if !bindings.insert(nonempty(&parameter.binding_id)?) {
                return Err("duplicate parameter binding ID");
            }
            let variable = parameter
                .variable
                .as_ref()
                .ok_or("missing parameter variable")?;
            if !parameter_names.insert(nonempty(&variable.name)?) {
                return Err("duplicate parameter name");
            }
            if let Some(id) = &variable.id {
                if !ids.insert(nonempty(id)?) {
                    return Err("duplicate variable ID");
                }
            }
            let ty = types.variable(variable, &model.namespace)?;
            types.charge(ty, expansion)?;
            parameters.push(ty);
        }
        registry.insert(
            (&model.namespace, &identity.id),
            Bkm {
                source: bkm,
                ty,
                parameters,
            },
        );
    }
    Ok(())
}

fn lookup<'a, 'm>(
    registry: &'a BTreeMap<Key<'m>, Bkm<'m>>,
    reference: &DmnIrReference,
) -> Option<(Key<'m>, &'a Bkm<'m>)> {
    registry.iter().find_map(|(&key, value)| {
        (key.0 == reference.model_namespace && key.1 == reference.element_id)
            .then_some((key, value))
    })
}

pub(super) fn knowledge<'m>(
    owner: Key<'m>,
    references: &[DmnIrReference],
    registry: &BTreeMap<Key<'m>, Bkm<'m>>,
    imports: &BTreeMap<&'m str, BTreeSet<&'m str>>,
    enabled: bool,
) -> Check<BTreeSet<Key<'m>>> {
    if !enabled && !references.is_empty() {
        return Err("knowledge dependencies require profile 0.10");
    }
    let mut result = BTreeSet::new();
    for reference in references {
        let (target, _) = lookup(registry, reference).ok_or("unresolved BKM dependency")?;
        if target == owner || !result.insert(target) {
            return Err("invalid BKM knowledge dependency");
        }
        if target.0 != owner.0 && !imports[owner.0].contains(target.0) {
            return Err("BKM dependency requires a direct import");
        }
    }
    Ok(result)
}

pub(super) fn graph<'m>(
    registry: &BTreeMap<Key<'m>, Bkm<'m>>,
    imports: &BTreeMap<&'m str, BTreeSet<&'m str>>,
) -> Check<BTreeMap<Key<'m>, BTreeSet<Key<'m>>>> {
    let mut graph = BTreeMap::new();
    for (&owner, bkm) in registry {
        graph.insert(
            owner,
            knowledge(
                owner,
                &bkm.source.knowledge_dependencies,
                registry,
                imports,
                true,
            )?,
        );
    }
    fn height<'m>(
        node: Key<'m>,
        graph: &BTreeMap<Key<'m>, BTreeSet<Key<'m>>>,
        active: &mut BTreeSet<Key<'m>>,
        heights: &mut BTreeMap<Key<'m>, usize>,
        depth: usize,
    ) -> Check<usize> {
        if depth > 32 {
            return Err("BKM knowledge depth exceeded");
        }
        if let Some(&height) = heights.get(&node) {
            if depth + height - 1 > 32 {
                return Err("BKM knowledge depth exceeded");
            }
            return Ok(height);
        }
        if !active.insert(node) {
            return Err("cyclic BKM knowledge dependency");
        }
        let mut result = 1;
        for &child in &graph[&node] {
            result = result.max(1 + height(child, graph, active, heights, depth + 1)?);
        }
        active.remove(&node);
        heights.insert(node, result);
        Ok(result)
    }
    let mut heights = BTreeMap::new();
    for &node in graph.keys() {
        height(node, &graph, &mut BTreeSet::new(), &mut heights, 1)?;
    }
    Ok(graph)
}

pub(super) fn input_bounds(ty: Type, types: &Types<'_>) -> Option<NumericBounds> {
    (ty==DmnIrScalarType::ScalarTypeNumber || matches!(ty,Type::Collection(index) if types.collections[index]==DmnIrScalarType::ScalarTypeNumber)).then_some(NumericBounds::INPUT)
}

impl ExpressionEmitter<'_, '_> {
    pub(super) fn bkm_call(&mut self, call: &DmnIrBkmCall, ty: Type) -> Check<Emitted> {
        let registry = self.bkms;
        let (target, bkm) = lookup(registry, call.target.as_ref().ok_or("missing BKM target")?)
            .ok_or("unresolved BKM target")?;
        if !self.knowledge.contains(&target) {
            return Err("BKM call requires declared knowledge dependency");
        }
        if bkm.ty != ty {
            return Err("BKM call result type mismatch");
        }
        if call.arguments.len() != bkm.parameters.len() {
            return Err("BKM argument count mismatch");
        }
        *self.call_serial += 1;
        let serial = *self.call_serial;
        let mut prefix = String::from("{ ");
        let mut positions = BTreeSet::new();
        let mut locals = BTreeMap::new();
        let mut depth = 0;
        for argument in &call.arguments {
            let position = argument.parameter_position as usize;
            if position == 0 || position > bkm.parameters.len() || !positions.insert(position) {
                return Err("invalid BKM argument position");
            }
            let mut value =
                self.expression(argument.expression.as_ref().ok_or("missing BKM argument")?)?;
            let expected = bkm.parameters[position - 1];
            if !assignable(value.ty, expected) {
                return Err("BKM argument type mismatch");
            }
            depth = depth.max(value.depth);
            write!(prefix, "let b{serial}_{position} = {}; ", value.value).unwrap();
            value.ty = expected;
            value.value = format!("b{serial}_{position}.clone()");
            locals.insert(
                bkm.source.parameters[position - 1].binding_id.clone(),
                value,
            );
        }
        let outer_locals = std::mem::replace(&mut self.locals, locals);
        let outer_bindings = std::mem::replace(
            &mut self.binding_ids,
            bkm.source
                .parameters
                .iter()
                .map(|p| p.binding_id.clone())
                .collect(),
        );
        let outer_dependencies = std::mem::take(&mut self.dependencies);
        let outer_knowledge =
            std::mem::replace(&mut self.knowledge, self.knowledge_graph[&target].clone());
        let outer_namespace = std::mem::replace(&mut self.namespace, target.0);
        let outer_ids = std::mem::replace(&mut self.validate_ids, false);
        let outer_table = std::mem::replace(&mut self.table_cell, false);
        let outer_filter = std::mem::replace(&mut self.filter_predicate, false);
        let mut result = self.expression(bkm.source.body.as_ref().ok_or("missing BKM body")?)?;
        self.locals = outer_locals;
        self.binding_ids = outer_bindings;
        self.dependencies = outer_dependencies;
        self.knowledge = outer_knowledge;
        self.namespace = outer_namespace;
        self.validate_ids = outer_ids;
        self.table_cell = outer_table;
        self.filter_predicate = outer_filter;
        if !assignable(result.ty, bkm.ty) {
            return Err("BKM body type mismatch");
        }
        write!(prefix, "{} }}", result.value).unwrap();
        result.ty = ty;
        result.value = prefix;
        result.depth = result.depth.max(depth);
        Ok(result)
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use prost::Message;
    fn fixture() -> DmnIrArtifact {
        DmnIrArtifact::decode(include_bytes!("../tests/fixtures/bkms-0.10.dmnir").as_slice())
            .unwrap()
    }
    #[test]
    fn rejects_bkm_call_and_declaration_errors() {
        for (edit, expected) in [
            (
                (|a: &mut DmnIrArtifact| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:policy")
                        .unwrap();
                    m.decisions
                        .iter_mut()
                        .find(|d| d.identity.as_ref().unwrap().id == "difference")
                        .unwrap()
                        .knowledge_dependencies
                        .clear();
                }) as fn(&mut DmnIrArtifact),
                "BKM call requires declared knowledge dependency",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:catalog")
                        .unwrap();
                    let b = m
                        .bkms
                        .iter_mut()
                        .find(|b| b.identity.as_ref().unwrap().id == "subtract")
                        .unwrap();
                    b.parameters[1].binding_id = b.parameters[0].binding_id.clone();
                },
                "duplicate parameter binding ID",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:catalog")
                        .unwrap();
                    m.bkms
                        .iter_mut()
                        .find(|b| b.identity.as_ref().unwrap().id == "keep")
                        .unwrap()
                        .body = None;
                },
                "missing BKM body",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:policy")
                        .unwrap();
                    let d = m
                        .decisions
                        .iter_mut()
                        .find(|d| d.identity.as_ref().unwrap().id == "difference")
                        .unwrap();
                    let Some(Node::BkmCall(c)) = &mut d.expression.as_mut().unwrap().node else {
                        panic!()
                    };
                    c.arguments[0].parameter_position = 0;
                },
                "invalid BKM argument position",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:policy")
                        .unwrap();
                    let d = m
                        .decisions
                        .iter_mut()
                        .find(|d| d.identity.as_ref().unwrap().id == "difference")
                        .unwrap();
                    let Some(Node::BkmCall(c)) = &mut d.expression.as_mut().unwrap().node else {
                        panic!()
                    };
                    c.arguments.pop();
                },
                "BKM argument count mismatch",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:policy")
                        .unwrap();
                    m.decisions
                        .iter_mut()
                        .find(|d| d.identity.as_ref().unwrap().id == "difference")
                        .unwrap()
                        .expression
                        .as_mut()
                        .unwrap()
                        .r#type = 4;
                },
                "BKM call result type mismatch",
            ),
        ] {
            let mut artifact = fixture();
            edit(&mut artifact);
            assert_eq!(verify_and_generate(&artifact), Err(expected));
        }
        let mut old = fixture();
        old.schema_version.as_mut().unwrap().minor = Some(9);
        old.semantics_version = "finmsg-dmnir-builtin-calls/0".into();
        old.required_capabilities.pop();
        assert_eq!(verify_and_generate(&old), Err("BKMs require profile 0.10"));
    }
    #[test]
    fn rejects_cycles_and_bounds_inline_expansion() {
        let mut cyclic = fixture();
        let catalog = cyclic
            .models
            .iter_mut()
            .find(|m| m.namespace == "urn:collections:catalog")
            .unwrap();
        catalog.imports.push(DmnIrImport {
            model_namespace: "urn:collections:policy".into(),
            name: "root".into(),
            ..Default::default()
        });
        catalog
            .bkms
            .iter_mut()
            .find(|b| b.identity.as_ref().unwrap().id == "subtract")
            .unwrap()
            .knowledge_dependencies
            .push(DmnIrReference {
                model_namespace: "urn:collections:policy".into(),
                element_id: "adjust".into(),
            });
        assert_eq!(
            verify_and_generate(&cyclic),
            Err("cyclic BKM knowledge dependency")
        );
        let mut artifact = fixture();
        let model = artifact
            .models
            .iter_mut()
            .find(|m| m.namespace == "urn:collections:catalog")
            .unwrap();
        let ns = model.namespace.clone();
        for index in 0..4 {
            let id = format!("wide-{index}");
            let (body, knowledge) = if index == 0 {
                (
                    DmnIrExpression {
                        r#type: 3,
                        node: Some(Node::Literal(DmnIrLiteral {
                            value: Some(Value::NumberValue(DmnIrDecimal {
                                unscaled_value: "0".into(),
                                scale: Some(0),
                            })),
                        })),
                        ..Default::default()
                    },
                    vec![],
                )
            } else {
                let reference = DmnIrReference {
                    model_namespace: ns.clone(),
                    element_id: format!("wide-{}", index - 1),
                };
                let mut e = DmnIrExpression {
                    r#type: 3,
                    node: Some(Node::BkmCall(DmnIrBkmCall {
                        target: Some(reference.clone()),
                        arguments: vec![],
                    })),
                    ..Default::default()
                };
                for _ in 0..7 {
                    e = DmnIrExpression {
                        r#type: 3,
                        node: Some(Node::Binary(Box::new(DmnIrBinaryExpression {
                            operator: 1,
                            left: Some(Box::new(e.clone())),
                            right: Some(Box::new(e)),
                        }))),
                        ..Default::default()
                    };
                }
                (e, vec![reference])
            };
            model.bkms.push(DmnIrBkm {
                identity: Some(DmnIrElementIdentity {
                    id: id.clone(),
                    name: id.clone(),
                }),
                variable: Some(DmnIrVariable {
                    name: id,
                    r#type: 3,
                    ..Default::default()
                }),
                body: Some(body),
                knowledge_dependencies: knowledge,
                ..Default::default()
            });
        }
        assert!(crate::wire::verify(&artifact.encode_to_vec()).is_ok());
        assert_eq!(
            verify_and_generate(&artifact),
            Err("expanded expression budget exceeded")
        );
    }
}
