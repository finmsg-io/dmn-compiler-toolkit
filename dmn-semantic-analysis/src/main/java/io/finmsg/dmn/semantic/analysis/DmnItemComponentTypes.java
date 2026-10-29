package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.*;

/** Expands inline item components without discarding nested declared fields. */
final class DmnItemComponentTypes {
	private DmnItemComponentTypes() {
	}

	static TypeReference type(ItemComponent component) {
		TypeReference result = component.getType();
		if (component.getComponentsCount() > 0) {
			var context = ContextTypeReference.newBuilder();
			for (ItemComponent child : component.getComponentsList())
				context.addEntries(
						ContextEntryTypeReference.newBuilder().setName(child.getNode().getName()).setType(type(child)));
			result = TypeReference.newBuilder().setContext(context).build();
		}
		return component.getIsCollection()
				? TypeReference.newBuilder().setList(ListTypeReference.newBuilder().setElementType(result)).build()
				: result;
	}
}
