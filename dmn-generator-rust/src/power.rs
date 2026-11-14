use crate::proto::{DmnIrExpression, dmn_ir_expression::Node, dmn_ir_literal::Value};
const MAX_EXACT: i128 = 1i128 << 53;

pub fn constant_power(
    left: &DmnIrExpression,
    right: &DmnIrExpression,
) -> Result<(String, i32), &'static str> {
    let base = integer(left).ok_or("power requires bounded constant integer operands")?;
    let exponent = integer(right)
        .filter(|n| (0..=53).contains(n))
        .ok_or("power requires bounded constant integer operands")?;
    let value = base
        .checked_pow(exponent as u32)
        .filter(|n| (-MAX_EXACT..=MAX_EXACT).contains(n))
        .ok_or("power result exceeds exact integer domain")?;
    if value == 0 {
        return Ok(("0".into(), 1));
    }
    if value.abs() < 10_000_000 {
        return Ok((format!("{value}0"), 1));
    }
    let digits = value.abs().to_string();
    let trimmed = digits.trim_end_matches('0');
    let mut coefficient = trimmed.to_owned();
    let mut scale = -((digits.len() - trimmed.len()) as i32);
    if coefficient.len() == 1 {
        coefficient.push('0');
        scale += 1;
    }
    if value < 0 {
        coefficient.insert(0, '-');
    }
    Ok((coefficient, scale))
}
fn integer(expression: &DmnIrExpression) -> Option<i128> {
    match expression.node.as_ref()? {
        Node::Literal(literal) => {
            let Value::NumberValue(decimal) = literal.value.as_ref()? else {
                return None;
            };
            let scale = decimal.scale?;
            let mut coefficient = decimal.unscaled_value.clone();
            if scale > 0 {
                let places = scale as usize;
                if coefficient == "0" {
                    return Some(0);
                }
                if places >= coefficient.len()
                    || !coefficient.as_bytes()[coefficient.len() - places..]
                        .iter()
                        .all(|b| *b == b'0')
                {
                    return None;
                }
                coefficient.truncate(coefficient.len() - places);
            } else if scale < 0 {
                if coefficient == "0" {
                    return Some(0);
                }
                if coefficient.len() + (-scale) as usize > 18 {
                    return None;
                }
                coefficient.extend(std::iter::repeat_n('0', (-scale) as usize));
            }
            coefficient
                .parse::<i128>()
                .ok()
                .filter(|n| (-MAX_EXACT..=MAX_EXACT).contains(n))
        }
        Node::Unary(unary) => match unary.operator {
            1 => integer(unary.operand.as_deref()?),
            2 => integer(unary.operand.as_deref()?).map(|n| -n),
            _ => None,
        },
        _ => None,
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::*;
    use prost::Message;
    fn literal(coefficient: &str, scale: i32) -> DmnIrExpression {
        DmnIrExpression {
            r#type: 3,
            node: Some(Node::Literal(DmnIrLiteral {
                value: Some(Value::NumberValue(DmnIrDecimal {
                    unscaled_value: coefficient.into(),
                    scale: Some(scale),
                })),
            })),
            ..Default::default()
        }
    }
    #[test]
    fn rejects_general_and_out_of_bound_powers() {
        for (a, b, error) in [
            (
                literal("2", 0),
                literal("5", 1),
                "power requires bounded constant integer operands",
            ),
            (
                literal("2", 0),
                literal("-1", 0),
                "power requires bounded constant integer operands",
            ),
            (
                literal("2", 0),
                literal("54", 0),
                "power requires bounded constant integer operands",
            ),
            (
                literal("9007199254740993", 0),
                literal("0", 0),
                "power requires bounded constant integer operands",
            ),
            (
                literal("-170141183460469231731687303715884105728", 0),
                literal("1", 0),
                "power requires bounded constant integer operands",
            ),
            (
                literal("3", 0),
                literal("53", 0),
                "power result exceeds exact integer domain",
            ),
        ] {
            let mut artifact = DmnIrArtifact::decode(
                include_bytes!("../tests/fixtures/powers-0.1.dmnir").as_slice(),
            )
            .unwrap();
            artifact.models[0].decisions[0]
                .expression
                .as_mut()
                .unwrap()
                .node = Some(Node::Binary(Box::new(DmnIrBinaryExpression {
                operator: 5,
                left: Some(Box::new(a)),
                right: Some(Box::new(b)),
            })));
            assert_eq!(crate::model::verify_and_generate(&artifact), Err(error));
        }
    }
}
