use crate::proto::*;
use std::collections::BTreeMap;

type Check<T> = Result<T, &'static str>;

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Type {
    Scalar(DmnIrScalarType),
    Record(usize),
    Collection(usize),
}

impl From<DmnIrScalarType> for Type {
    fn from(value: DmnIrScalarType) -> Self {
        Self::Scalar(value)
    }
}

impl PartialEq<DmnIrScalarType> for Type {
    fn eq(&self, other: &DmnIrScalarType) -> bool {
        *self == Self::Scalar(*other)
    }
}

impl Type {
    pub fn source(self) -> String {
        match self {
            Self::Scalar(scalar) => format!("Type::Scalar({})", scalar as i32),
            Self::Collection(index) => format!("Type::Collection({index})"),
            Self::Record(index) => format!("Type::Record({index})"),
        }
    }
}

pub struct Record<'a> {
    pub fields: Vec<(&'a str, Type)>,
    pub constraints: Vec<Option<&'a DmnIrUnaryTests>>,
}

pub struct Types<'a> {
    enabled: bool,
    dates: bool,
    indices: BTreeMap<(&'a str, &'a str), usize>,
    collections_enabled: bool,
    collection_indices: BTreeMap<(&'a str, &'a str), usize>,
    pub collections: Vec<DmnIrScalarType>,
    pub records: Vec<Record<'a>>,
    costs: Vec<usize>,
    pub expansion: usize,
    models: &'a [DmnIrModel],
}

impl<'a> Types<'a> {
    pub fn new(
        models: &'a [DmnIrModel],
        enabled: bool,
        dates: bool,
        collections_enabled: bool,
    ) -> Check<Self> {
        let mut indices = BTreeMap::new();
        for model in models {
            if !enabled && !model.record_definitions.is_empty() {
                return Err("records require profile 0.2");
            }
            for record in &model.record_definitions {
                let identity = record.identity.as_ref().ok_or("missing record identity")?;
                let key = (model.namespace.as_str(), identity.id.as_str());
                if indices.insert(key, 0).is_some() {
                    return Err("duplicate record identity");
                }
            }
        }
        for (index, value) in indices.values_mut().enumerate() {
            *value = index;
        }
        let mut collection_indices = BTreeMap::new();
        for model in models {
            if !collections_enabled && !model.collection_definitions.is_empty() {
                return Err("collections require profile 0.7");
            }
            for collection in &model.collection_definitions {
                let identity = collection
                    .identity
                    .as_ref()
                    .ok_or("missing collection identity")?;
                if collection_indices
                    .insert((model.namespace.as_str(), identity.id.as_str()), 0)
                    .is_some()
                {
                    return Err("duplicate collection identity");
                }
            }
        }
        for (index, value) in collection_indices.values_mut().enumerate() {
            *value = index;
        }
        let mut collections = vec![DmnIrScalarType::ScalarTypeNull; collection_indices.len()];
        for model in models {
            for collection in &model.collection_definitions {
                let scalar = scalar_type(collection.element_type, dates)?;
                if scalar == DmnIrScalarType::ScalarTypeNull {
                    return Err("collection needs non-null scalar element type");
                }
                collections[collection_indices[&(
                    model.namespace.as_str(),
                    collection.identity.as_ref().unwrap().id.as_str(),
                )]] = scalar;
            }
        }
        let collections_expansion = collections.len() * 2;
        let mut types = Self {
            enabled,
            collections_enabled,
            collection_indices,
            collections,
            dates,
            records: (0..indices.len())
                .map(|_| Record {
                    fields: Vec::new(),
                    constraints: Vec::new(),
                })
                .collect(),
            costs: vec![0; indices.len()],
            indices,
            expansion: collections_expansion,
            models,
        };
        for model in models {
            for record in &model.record_definitions {
                if record.fields.is_empty() {
                    return Err("empty records are unsupported");
                }
                let identity = record.identity.as_ref().unwrap();
                let index = types.indices[&(model.namespace.as_str(), identity.id.as_str())];
                let mut fields = BTreeMap::new();
                for field in &record.fields {
                    if field.name.trim().is_empty()
                        || fields.insert(field.name.as_str(), ()).is_some()
                    {
                        return Err("invalid or duplicate record field name");
                    }
                    let ty = types.reference(
                        field.r#type.as_ref().ok_or("missing field type")?,
                        &model.namespace,
                    )?;
                    types.records[index].fields.push((&field.name, ty));
                    types.records[index]
                        .constraints
                        .push(field.constraint.as_ref());
                }
            }
        }
        let mut heights = vec![0; types.records.len()];
        let mut active = vec![false; types.records.len()];
        for index in 0..types.records.len() {
            types.cost(index, &mut heights, &mut active, 1)?;
            types.expansion += types.costs[index];
            if types.expansion > 50_000 {
                return Err("expanded record type budget exceeded");
            }
        }
        Ok(types)
    }

    fn cost(
        &mut self,
        index: usize,
        heights: &mut [usize],
        active: &mut [bool],
        depth: usize,
    ) -> Check<()> {
        if depth > 32 {
            return Err("record depth budget exceeded");
        }
        if self.costs[index] != 0 {
            if depth + heights[index] - 1 > 32 {
                return Err("record depth budget exceeded");
            }
            return Ok(());
        }
        if std::mem::replace(&mut active[index], true) {
            return Err("recursive records are unsupported");
        }
        let mut cost = 1;
        let mut height = 1;
        for position in 0..self.records[index].fields.len() {
            let ty = self.records[index].fields[position].1;
            let (field_cost, field_height) = if let Type::Record(child) = ty {
                self.cost(child, heights, active, depth + 1)?;
                (self.costs[child], heights[child])
            } else if matches!(ty, Type::Collection(_)) {
                (2, 2)
            } else {
                (1, 1)
            };
            cost += field_cost;
            height = height.max(field_height + 1);
            if cost > 50_000 {
                return Err("expanded record type budget exceeded");
            }
            if height > 32 {
                return Err("record depth budget exceeded");
            }
        }
        active[index] = false;
        self.costs[index] = cost;
        heights[index] = height;
        Ok(())
    }

    pub fn variable(&self, value: &DmnIrVariable, namespace: &str) -> Check<Type> {
        self.select(
            value.r#type,
            value.record_type.as_ref(),
            value.collection_type.as_ref(),
            namespace,
        )
    }

    pub fn expression(&self, value: &DmnIrExpression, namespace: &str) -> Check<Type> {
        self.select(
            value.r#type,
            value.record_type.as_ref(),
            value.collection_type.as_ref(),
            namespace,
        )
    }

    fn select(
        &self,
        scalar: i32,
        record: Option<&DmnIrReference>,
        collection: Option<&DmnIrReference>,
        namespace: &str,
    ) -> Check<Type> {
        if let Some(collection) = collection {
            if scalar != 0 || record.is_some() {
                return Err("scalar record and collection types are mutually exclusive");
            }
            return self.collection(collection, namespace);
        }
        if let Some(record) = record {
            if scalar != 0 {
                return Err("scalar and record types are mutually exclusive");
            }
            self.record(record, namespace)
        } else {
            Ok(Type::Scalar(scalar_type(scalar, self.dates)?))
        }
    }

    fn reference(&self, value: &DmnIrTypeReference, namespace: &str) -> Check<Type> {
        match value.kind.as_ref() {
            Some(dmn_ir_type_reference::Kind::ScalarType(scalar)) => {
                Ok(Type::Scalar(scalar_type(*scalar, self.dates)?))
            }
            Some(dmn_ir_type_reference::Kind::RecordType(record)) => self.record(record, namespace),
            Some(dmn_ir_type_reference::Kind::CollectionType(collection)) => {
                self.collection(collection, namespace)
            }
            _ => Err("unsupported record field type"),
        }
    }

    fn record(&self, value: &DmnIrReference, namespace: &str) -> Check<Type> {
        if !self.enabled {
            return Err("records require profile 0.2");
        }
        let index = self
            .indices
            .get(&(value.model_namespace.as_str(), value.element_id.as_str()))
            .copied()
            .ok_or("unresolved record type")?;
        if value.model_namespace != namespace
            && !self.models.iter().any(|model| {
                model.namespace == namespace
                    && model
                        .imports
                        .iter()
                        .any(|import| import.model_namespace == value.model_namespace)
            })
        {
            return Err("record type requires a direct import");
        }
        Ok(Type::Record(index))
    }

    fn collection(&self, value: &DmnIrReference, namespace: &str) -> Check<Type> {
        if !self.collections_enabled {
            return Err("collections require profile 0.7");
        }
        let index = self
            .collection_indices
            .get(&(value.model_namespace.as_str(), value.element_id.as_str()))
            .copied()
            .ok_or("unresolved collection type")?;
        if value.model_namespace != namespace
            && !self.models.iter().any(|m| {
                m.namespace == namespace
                    && m.imports
                        .iter()
                        .any(|i| i.model_namespace == value.model_namespace)
            })
        {
            return Err("collection type requires a direct import");
        }
        Ok(Type::Collection(index))
    }

    pub fn charge(&self, ty: Type, total: &mut usize) -> Check<()> {
        let cost = match ty {
            Type::Record(index) => self.costs[index],
            Type::Collection(_) => 2,
            _ => 0,
        };
        if cost != 0 {
            *total += cost;
            if *total > 50_000 {
                return Err("expanded record type budget exceeded");
            }
        }
        Ok(())
    }
}

pub fn scalar_type(value: i32, dates: bool) -> Check<DmnIrScalarType> {
    use DmnIrScalarType::*;
    match DmnIrScalarType::try_from(value) {
        Ok(value @ (ScalarTypeNull | ScalarTypeBoolean | ScalarTypeNumber | ScalarTypeString)) => {
            Ok(value)
        }
        Ok(ScalarTypeDate) if dates => Ok(ScalarTypeDate),
        _ => Err("type outside scalar profile"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use prost::Message;

    fn artifact() -> DmnIrArtifact {
        let bytes = include_bytes!("../tests/fixtures/records-0.2.dmnir");
        DmnIrArtifact::decode(bytes.as_slice()).unwrap()
    }

    fn root(artifact: &mut DmnIrArtifact) -> &mut DmnIrModel {
        artifact
            .models
            .iter_mut()
            .find(|model| model.namespace == "urn:rust-proof:records")
            .unwrap()
    }

    #[test]
    fn rejects_record_cycles_imports_and_duplicate_fields() {
        let mut value = artifact();
        let model = root(&mut value);
        model.record_definitions[0].fields[0].r#type =
            Some(record_type(&model.namespace, "applicant-type"));
        assert_eq!(
            crate::model::verify_and_generate(&value),
            Err("recursive records are unsupported")
        );

        let mut value = artifact();
        root(&mut value).imports.clear();
        assert_eq!(
            crate::model::verify_and_generate(&value),
            Err("record type requires a direct import")
        );

        let mut value = artifact();
        let record = &mut root(&mut value).record_definitions[0];
        record.fields.push(record.fields[0].clone());
        assert_eq!(
            crate::model::verify_and_generate(&value),
            Err("invalid or duplicate record field name")
        );
    }

    #[test]
    fn validates_nominal_types_and_field_results() {
        let mut value = artifact();
        let model = root(&mut value);
        let input = model
            .inputs
            .iter_mut()
            .find(|input| input.identity.as_ref().unwrap().id == "applicant")
            .unwrap();
        input.variable.as_mut().unwrap().r#type = DmnIrScalarType::ScalarTypeNumber as i32;
        assert_eq!(
            crate::model::verify_and_generate(&value),
            Err("scalar and record types are mutually exclusive")
        );

        let mut value = artifact();
        let model = root(&mut value);
        let balance = model
            .decisions
            .iter_mut()
            .find(|decision| decision.identity.as_ref().unwrap().id == "balance")
            .unwrap();
        balance
            .variable
            .as_mut()
            .unwrap()
            .record_type
            .as_mut()
            .unwrap()
            .element_id = "account".into();
        assert_eq!(
            crate::model::verify_and_generate(&value),
            Err("decision expression type mismatch")
        );

        let mut value = artifact();
        let model = root(&mut value);
        let amount = model
            .decisions
            .iter_mut()
            .find(|decision| decision.identity.as_ref().unwrap().id == "amount")
            .unwrap();
        amount.expression.as_mut().unwrap().r#type = DmnIrScalarType::ScalarTypeString as i32;
        assert_eq!(
            crate::model::verify_and_generate(&value),
            Err("record field result type mismatch")
        );

        let mut value = artifact();
        value.schema_version.as_mut().unwrap().minor = Some(1);
        value.semantics_version = "finmsg-dmnir-scalar-expressions/0".into();
        value.required_capabilities.pop();
        assert_eq!(
            crate::model::verify_and_generate(&value),
            Err("records require profile 0.2")
        );
    }

    #[test]
    fn bounds_record_depth_and_expansion() {
        for (count, repeated, error) in [
            (33, false, "record depth budget exceeded"),
            (16, true, "expanded record type budget exceeded"),
        ] {
            let mut value = artifact();
            let model = root(&mut value);
            let namespace = model.namespace.clone();
            let mut previous = DmnIrTypeReference {
                kind: Some(dmn_ir_type_reference::Kind::ScalarType(
                    DmnIrScalarType::ScalarTypeNumber as i32,
                )),
            };
            for index in 0..count {
                let id = format!("type-{index}");
                let mut fields = vec![DmnIrRecordField {
                    name: "First".into(),
                    r#type: Some(previous.clone()),
                    ..Default::default()
                }];
                if repeated {
                    fields.push(DmnIrRecordField {
                        name: "Second".into(),
                        r#type: Some(previous),
                        ..Default::default()
                    });
                }
                model.record_definitions.push(DmnIrRecordDefinition {
                    identity: Some(DmnIrElementIdentity {
                        id: id.clone(),
                        name: id.clone(),
                    }),
                    fields,
                });
                previous = record_type(&namespace, &id);
            }
            assert_eq!(crate::model::verify_and_generate(&value), Err(error));
        }
    }

    fn collections() -> DmnIrArtifact {
        DmnIrArtifact::decode(include_bytes!("../tests/fixtures/collections-0.7.dmnir").as_slice())
            .unwrap()
    }

    #[test]
    fn validates_collection_identity_elements_and_profiles() {
        let valid = collections();
        for (edit, expected) in [
            (
                (|a: &mut DmnIrArtifact| {
                    a.models
                        .iter_mut()
                        .find(|m| !m.collection_definitions.is_empty())
                        .unwrap()
                        .collection_definitions[0]
                        .element_type = 1;
                }) as fn(&mut DmnIrArtifact),
                "collection needs non-null scalar element type",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| !m.collection_definitions.is_empty())
                        .unwrap();
                    m.collection_definitions
                        .push(m.collection_definitions[0].clone());
                },
                "duplicate collection identity",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:policy")
                        .unwrap();
                    m.inputs
                        .iter_mut()
                        .find(|i| i.identity.as_ref().unwrap().id == "external")
                        .unwrap()
                        .variable
                        .as_mut()
                        .unwrap()
                        .r#type = 3;
                },
                "scalar record and collection types are mutually exclusive",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:policy")
                        .unwrap();
                    m.inputs
                        .iter_mut()
                        .find(|i| i.identity.as_ref().unwrap().id == "external")
                        .unwrap()
                        .variable
                        .as_mut()
                        .unwrap()
                        .collection_type
                        .as_mut()
                        .unwrap()
                        .element_id = "missing".into();
                },
                "unresolved collection type",
            ),
            (
                |a| {
                    let m = a
                        .models
                        .iter_mut()
                        .find(|m| m.namespace == "urn:collections:policy")
                        .unwrap();
                    let e = m
                        .decisions
                        .iter_mut()
                        .find(|d| d.identity.as_ref().unwrap().id == "flags")
                        .unwrap()
                        .expression
                        .as_mut()
                        .unwrap();
                    let Some(dmn_ir_expression::Node::List(list)) = &mut e.node else {
                        panic!()
                    };
                    list.elements[0] = DmnIrExpression {
                        r#type: 4,
                        node: Some(dmn_ir_expression::Node::Literal(DmnIrLiteral {
                            value: Some(dmn_ir_literal::Value::StringValue("true".into())),
                        })),
                        ..Default::default()
                    };
                },
                "list element type mismatch",
            ),
        ] {
            let mut artifact = valid.clone();
            edit(&mut artifact);
            assert_eq!(crate::model::verify_and_generate(&artifact), Err(expected));
        }
        let mut old = valid;
        old.schema_version.as_mut().unwrap().minor = Some(6);
        old.semantics_version = "finmsg-dmnir-field-constraints/0".into();
        old.required_capabilities.pop();
        assert_eq!(
            crate::model::verify_and_generate(&old),
            Err("collections require profile 0.7")
        );
    }

    #[test]
    fn preserves_nominal_collection_identity() {
        let mut artifact = collections();
        let catalog = artifact
            .models
            .iter_mut()
            .find(|m| m.namespace == "urn:collections:catalog")
            .unwrap();
        let mut other = catalog
            .collection_definitions
            .iter()
            .find(|c| c.identity.as_ref().unwrap().id == "fees")
            .unwrap()
            .clone();
        other.identity.as_mut().unwrap().id = "other-fees".into();
        other.identity.as_mut().unwrap().name = "OtherFees".into();
        catalog.collection_definitions.push(other);
        let root = artifact
            .models
            .iter_mut()
            .find(|m| m.namespace == "urn:collections:policy")
            .unwrap();
        let echo = root
            .decisions
            .iter_mut()
            .find(|d| d.identity.as_ref().unwrap().id == "echo")
            .unwrap();
        echo.expression
            .as_mut()
            .unwrap()
            .collection_type
            .as_mut()
            .unwrap()
            .element_id = "other-fees".into();
        assert_eq!(
            crate::model::verify_and_generate(&artifact),
            Err("reference type mismatch")
        );
    }

    #[test]
    fn collection_fields_count_toward_record_depth() {
        for count in [30, 31] {
            let mut artifact = collections();
            let catalog = artifact
                .models
                .iter_mut()
                .find(|m| m.namespace == "urn:collections:catalog")
                .unwrap();
            let ns = catalog.namespace.clone();
            let mut previous = DmnIrTypeReference {
                kind: Some(dmn_ir_type_reference::Kind::CollectionType(
                    DmnIrReference {
                        model_namespace: ns.clone(),
                        element_id: "fees".into(),
                    },
                )),
            };
            for index in 0..count {
                let id = format!("depth-{index}");
                catalog.record_definitions.push(DmnIrRecordDefinition {
                    identity: Some(DmnIrElementIdentity {
                        id: id.clone(),
                        name: id.clone(),
                    }),
                    fields: vec![DmnIrRecordField {
                        name: "Next".into(),
                        r#type: Some(previous),
                        ..Default::default()
                    }],
                });
                previous = record_type(&ns, &id);
            }
            let result = crate::model::verify_and_generate(&artifact);
            if count == 30 {
                assert!(result.is_ok());
            } else {
                assert_eq!(result, Err("record depth budget exceeded"));
            }
        }
    }

    #[test]
    fn collection_references_require_direct_imports() {
        let mut artifact = collections();
        let root = artifact
            .models
            .iter_mut()
            .find(|m| m.namespace == "urn:collections:policy")
            .unwrap();
        root.imports.clear();
        root.record_definitions.clear();
        root.inputs
            .retain(|i| i.identity.as_ref().unwrap().id == "external");
        root.decisions
            .retain(|d| d.identity.as_ref().unwrap().id == "echo");
        artifact
            .evaluation_order
            .retain(|r| r.model_namespace == "urn:collections:policy" && r.element_id == "echo");
        assert_eq!(
            crate::model::verify_and_generate(&artifact),
            Err("collection type requires a direct import")
        );
    }

    fn record_type(namespace: &str, id: &str) -> DmnIrTypeReference {
        DmnIrTypeReference {
            kind: Some(dmn_ir_type_reference::Kind::RecordType(DmnIrReference {
                model_namespace: namespace.into(),
                element_id: id.into(),
            })),
        }
    }
}
