package io.github.datakore.jsont.listener;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.errors.collector.ErrorCollector;

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
