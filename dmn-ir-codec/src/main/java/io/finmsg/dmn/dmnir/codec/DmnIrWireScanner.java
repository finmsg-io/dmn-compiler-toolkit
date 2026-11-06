package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader.check;
import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrArtifact;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrModel;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrInput;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrDecision;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrReference;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrRecordDefinition;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrRecordField;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrLocalBinding;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrLocalReference;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Allocation-bounded schema-aware scan; string bytes are skipped, not
 * materialized.
 */
final class DmnIrWireScanner {
	private final DmnIrLimits limits;
	private int messages;
	private int fields;
	private int models;
	private int elements;
	private int references;

	DmnIrWireScanner(DmnIrLimits limits) {
		this.limits = limits;
	}

	void scan(byte[] bytes) throws IOException {
		CodedInputStream input = CodedInputStream.newInstance(bytes);
		input.setSizeLimit(limits.maxBytes());
		message(input, DmnIrArtifact.getDescriptor(), "artifact", 1);
	}

	private void message(CodedInputStream input, Descriptor descriptor, String path, int depth) throws IOException {
		check(depth <= limits.maxDepth() && ++messages <= limits.maxMessages(), LIMIT_EXCEEDED, path,
				"Wire depth or message budget exceeded.");
		if (descriptor == DmnIrModel.getDescriptor()) {
			check(++models <= limits.maxModels(), LIMIT_EXCEEDED, path, "Model budget exceeded.");
		} else if (descriptor == DmnIrInput.getDescriptor() || descriptor == DmnIrDecision.getDescriptor()
				|| descriptor == DmnIrRecordDefinition.getDescriptor() || descriptor == DmnIrRecordField.getDescriptor()
				|| descriptor == DmnIrLocalBinding.getDescriptor()) {
			check(++elements <= limits.maxElements(), LIMIT_EXCEEDED, path, "Declaration budget exceeded.");
		} else if (descriptor == DmnIrReference.getDescriptor() || descriptor == DmnIrLocalReference.getDescriptor()) {
			check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
		}
		Set<Integer> singular = new HashSet<>();
		Set<Integer> oneofs = new HashSet<>();
		Map<Integer, Integer> repeated = new HashMap<>();
		int tag;
		while ((tag = input.readTag()) != 0) {
			check(++fields <= limits.maxFields(), LIMIT_EXCEEDED, path, "Wire field budget exceeded.");
			FieldDescriptor field = descriptor.findFieldByNumber(tag >>> 3);
			check(field != null, UNKNOWN_FIELD, path, "Unknown wire field in the experimental profile.");
			String fieldPath = path + "." + field.getName();
			if (field.isRepeated()) {
				int index = repeated.merge(field.getNumber(), 1, Integer::sum) - 1;
				fieldPath += "[" + index + "]";
			} else {
				check(singular.add(field.getNumber()), MALFORMED_PROTOBUF, fieldPath, "Duplicate singular field.");
				if (field.getContainingOneof() != null) {
					check(oneofs.add(field.getContainingOneof().getIndex()), MALFORMED_PROTOBUF, fieldPath,
							"Multiple fields select the same oneof.");
				}
			}
			boolean message = field.getJavaType() == FieldDescriptor.JavaType.MESSAGE;
			boolean string = field.getJavaType() == FieldDescriptor.JavaType.STRING;
			check((tag & 7) == (message || string ? 2 : 0), MALFORMED_PROTOBUF, fieldPath, "Wrong wire type.");
			if (message || string) {
				long wireLength = input.readRawVarint64();
				check(wireLength >= 0 && wireLength <= limits.maxBytes(), MALFORMED_PROTOBUF, fieldPath,
						"Invalid length-delimited field.");
				int length = (int) wireLength;
				if (string) {
					check(length <= limits.maxStringBytes(), LIMIT_EXCEEDED, fieldPath, "String byte budget exceeded.");
					input.skipRawBytes(length);
				} else {
					int previous = input.pushLimit(length);
					try {
						message(input, field.getMessageType(), fieldPath, depth + 1);
						check(input.getBytesUntilLimit() == 0, MALFORMED_PROTOBUF, fieldPath, "Truncated message.");
					} finally {
						input.popLimit(previous);
					}
				}
			} else {
				long value = input.readRawVarint64();
				check((value & ~0xffffffffL) == 0, MALFORMED_PROTOBUF, fieldPath, "Scalar exceeds 32-bit wire range.");
				if (field.getJavaType() == FieldDescriptor.JavaType.BOOLEAN) {
					check(value <= 1, MALFORMED_PROTOBUF, fieldPath, "Invalid boolean encoding.");
				}
			}
		}
	}
}
