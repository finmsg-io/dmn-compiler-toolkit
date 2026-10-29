package io.finmsg.dmn.grpc;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DmnGrpcValueConverterTest {
	private Object roundTrip(Object value) throws Exception {
		return DmnGrpcValueConverter
				.toJavaObject(Value.parseFrom(DmnGrpcValueConverter.toProtoValue(value).toByteArray()));
	}

	@Test
	void nestedNullMembersSurviveProtobufSerialization() throws Exception {
		var context = new LinkedHashMap<String, Object>();
		context.put("missing", null);
		context.put("values", Arrays.asList(BigDecimal.ONE, null, "null"));
		var value = Arrays.asList(null, context);
		assertThat(roundTrip(value)).isEqualTo(value);
	}

	@Test
	void temporalOffsetsZonesAndNanosecondsSurviveSerialization() throws Exception {
		for (Object value : List.of(LocalTime.parse("12:34:56.123456789"), OffsetTime.parse("12:34:56.123456789+05:30"),
				LocalDateTime.parse("2024-03-31T01:30:00.123456789"),
				OffsetDateTime.parse("2024-03-31T01:30:00.123456789+01:00"),
				ZonedDateTime.parse("2024-03-31T01:30:00.123456789+01:00[Europe/Paris]"),
				new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime(LocalTime.NOON, ZoneId.of("Europe/Paris")),
				new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(LocalDateTime.of(2024, 3, 31, 1, 30),
						ZoneId.of("Europe/Paris")))) {
			assertThat(roundTrip(value)).as(value.toString()).isEqualTo(value);
		}
	}

	@Test
	void decimalsAndDurationKindsRemainExact() throws Exception {
		for (Object value : List.of(new BigDecimal("123456789012345678901234567890.123456789"), Duration.ZERO,
				Period.ZERO, Duration.ofSeconds(-1, 123), Period.of(1, 2, 0))) {
			Object actual = roundTrip(value);
			assertThat(actual).isExactlyInstanceOf(value.getClass()).isEqualTo(value);
		}
	}
}
