use crate::proto::*;
use std::collections::BTreeMap;

type Check<T> = Result<T, &'static str>;

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Type {
    Scalar(DmnIrScalarType),
    Record(usize),
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
            Self::Record(index) => format!("Type::Record({index})"),
        }
    }
}

pub struct Record<'a> {
    pub fields: Vec<(&'a str, Type)>,
}

pub struct Types<'a> {
    enabled: bool,
    indices: BTreeMap<(&'a str, &'a str), usize>,
    pub records: Vec<Record<'a>>,
    costs: Vec<usize>,
    pub expansion: usize,
    models: &'a [DmnIrModel],
}

impl<'a> Types<'a> {
    pub fn new(models: &'a [DmnIrModel], enabled: bool) -> Check<Self> {
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
        let mut types = Self {
            enabled,
            records: (0..indices.len())
                .map(|_| Record { fields: Vec::new() })
                .collect(),
            costs: vec![0; indices.len()],
            indices,
            expansion: 0,
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
        self.select(value.r#type, value.record_type.as_ref(), namespace)
    }

    pub fn expression(&self, value: &DmnIrExpression, namespace: &str) -> Check<Type> {
        self.select(value.r#type, value.record_type.as_ref(), namespace)
    }

    fn select(&self, scalar: i32, record: Option<&DmnIrReference>, namespace: &str) -> Check<Type> {
        if let Some(record) = record {
            if scalar != 0 {
                return Err("scalar and record types are mutually exclusive");
            }
            self.record(record, namespace)
        } else {
            Ok(Type::Scalar(scalar_type(scalar)?))
        }
    }

    fn reference(&self, value: &DmnIrTypeReference, namespace: &str) -> Check<Type> {
        match value.kind.as_ref() {
            Some(dmn_ir_type_reference::Kind::ScalarType(scalar)) => {
                Ok(Type::Scalar(scalar_type(*scalar)?))
            }
            Some(dmn_ir_type_reference::Kind::RecordType(record)) => self.record(record, namespace),
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

    pub fn charge(&self, ty: Type, total: &mut usize) -> Check<()> {
        if let Type::Record(index) = ty {
            *total += self.costs[index];
            if *total > 50_000 {
                return Err("expanded record type budget exceeded");
            }
        }
        Ok(())
    }
}

pub fn scalar_type(value: i32) -> Check<DmnIrScalarType> {
    use DmnIrScalarType::*;
    match DmnIrScalarType::try_from(value) {
        Ok(value @ (ScalarTypeNull | ScalarTypeBoolean | ScalarTypeNumber | ScalarTypeString)) => {
            Ok(value)
        }
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

    fn record_type(namespace: &str, id: &str) -> DmnIrTypeReference {
        DmnIrTypeReference {
            kind: Some(dmn_ir_type_reference::Kind::RecordType(DmnIrReference {
                model_namespace: namespace.into(),
                element_id: id.into(),
            })),
        }
    }
}
