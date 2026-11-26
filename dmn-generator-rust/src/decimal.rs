pub(crate) fn valid_decimal(coefficient: &str, scale: i32) -> bool {
    let digits = coefficient.strip_prefix('-').unwrap_or(coefficient);
    scale.unsigned_abs() <= 4096
        && !digits.is_empty()
        && digits.len() <= 4096
        && (coefficient == "0" || !digits.starts_with('0'))
        && digits.bytes().all(|byte| byte.is_ascii_digit())
}
