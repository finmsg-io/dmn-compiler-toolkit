package io.finmsg.dmn.feel.parser;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

final class FeelErrorListener extends BaseErrorListener {

	private final List<FeelDiagnostic> diagnostics = new ArrayList<>();

	@Override
	public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
			String message, RecognitionException exception) {
		diagnostics.add(new FeelDiagnostic(line, charPositionInLine, message));
	}

	List<FeelDiagnostic> diagnostics() {
		return List.copyOf(diagnostics);
	}
}
