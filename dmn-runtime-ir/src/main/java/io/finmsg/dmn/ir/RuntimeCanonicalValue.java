package io.finmsg.dmn.ir;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

/**
 * Parsed, evaluator-ready value stored in an optimized Runtime IR constant
 * pool.
 */
public sealed interface RuntimeCanonicalValue {
	record NullValue() implements RuntimeCanonicalValue {
	}
	record BooleanValue(boolean value) implements RuntimeCanonicalValue {
	}
	record NumberValue(BigDecimal value) implements RuntimeCanonicalValue {
	}
	record StringValue(String value) implements RuntimeCanonicalValue {
	}
	record DateValue(LocalDate value) implements RuntimeCanonicalValue {
	}
	record TimeValue(LocalTime value, Optional<ZoneOffset> offset) implements RuntimeCanonicalValue {
	}
	record DateTimeValue(LocalDateTime value, Optional<ZoneOffset> offset) implements RuntimeCanonicalValue {
	}
	record DurationValue(TemporalAmount value) implements RuntimeCanonicalValue {
	}
}
