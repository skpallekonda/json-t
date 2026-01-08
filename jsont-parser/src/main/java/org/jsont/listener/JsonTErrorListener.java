package org.jsont.listener;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.jsont.errors.ErrorLocation;
import org.jsont.errors.Severity;
import org.jsont.errors.ValidationError;
import org.jsont.errors.collector.ErrorCollector;

public class JsonTErrorListener extends BaseErrorListener {
    private final ErrorCollector errorCollector;

    public JsonTErrorListener(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer, Object offendingSymbol,
            int line, int charPositionInLine, String msg, RecognitionException e
    ) {
        errorCollector.report(new ValidationError(
                Severity.FATAL, String.format("Syntax error at line %d : %d - %s", line, charPositionInLine, msg),
                ErrorLocation.withCell(line, charPositionInLine)
        ));
    }
}
