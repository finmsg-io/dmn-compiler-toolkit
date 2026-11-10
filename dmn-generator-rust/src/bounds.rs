const MAX_COEFFICIENT_DIGITS: usize = 32_768;
const MAX_ABSOLUTE_SCALE: i32 = 8_192;
pub const MAX_LIMB_PRODUCTS: usize = 8_388_608;
pub const LIMB_DIGITS: usize = 9;

#[derive(Clone, Copy)]
pub struct NumericBounds {
    pub digits: usize,
    min_scale: i32,
    max_scale: i32,
    // Upper bound of digits minus scale for nonzero values.
    magnitude: Option<i32>,
}

impl NumericBounds {
    pub const INPUT: Self = Self {
        digits: 4_096,
        min_scale: -4_096,
        max_scale: 4_096,
        magnitude: Some(8_192),
    };

    pub fn literal(coefficient: &str, scale: i32) -> Self {
        let digits = coefficient.trim_start_matches('-').len();
        Self {
            digits,
            min_scale: scale,
            max_scale: scale,
            magnitude: (coefficient != "0").then_some(digits as i32 - scale),
        }
    }

    pub fn join(self, other: Self) -> Self {
        Self {
            digits: self.digits.max(other.digits),
            min_scale: self.min_scale.min(other.min_scale),
            max_scale: self.max_scale.max(other.max_scale),
            magnitude: self.magnitude.max(other.magnitude),
        }
    }

    pub fn sum(self, other: Self) -> Self {
        let max_scale = self.max_scale.max(other.max_scale);
        let magnitude = match (self.magnitude, other.magnitude) {
            (Some(a), Some(b)) => Some(a.max(b) + 1),
            (a, b) => a.or(b),
        };
        Self {
            digits: magnitude.map_or(1, |bound| (bound + max_scale).max(1) as usize),
            min_scale: self.min_scale.max(other.min_scale),
            max_scale,
            magnitude,
        }
    }

    pub fn product(self, other: Self) -> Self {
        let magnitude = self.magnitude.zip(other.magnitude).map(|(a, b)| a + b);
        Self {
            digits: magnitude.map_or(1, |_| self.digits + other.digits),
            min_scale: self.min_scale + other.min_scale,
            max_scale: self.max_scale + other.max_scale,
            magnitude,
        }
    }

    pub fn limb_products(self, other: Self) -> usize {
        self.digits.div_ceil(LIMB_DIGITS) * other.digits.div_ceil(LIMB_DIGITS)
    }

    pub fn verify(self) -> Result<(), &'static str> {
        if self.digits > MAX_COEFFICIENT_DIGITS {
            return Err("decimal coefficient bound exceeds 32768 digits");
        }
        if self.min_scale < -MAX_ABSOLUTE_SCALE || self.max_scale > MAX_ABSOLUTE_SCALE {
            return Err("decimal scale bound exceeds 8192");
        }
        Ok(())
    }
}
