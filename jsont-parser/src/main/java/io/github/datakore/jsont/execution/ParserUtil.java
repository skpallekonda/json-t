package io.github.datakore.jsont.execution;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.JsonTLexer;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.listener.DataListener;
import io.github.datakore.jsont.listener.JsonTErrorListener;

public final class ParserUtil {

    public static JsonTParser createParser(CharStream input, ErrorCollector errorCollector) {
        JsonTErrorListener errorListener = new JsonTErrorListener(errorCollector);
        JsonTLexer lexer = new JsonTLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        tokens.fill();

        JsonTParser parser = new JsonTParser(tokens);
        parser.addParseListener(new DataListener());
        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        parser.addErrorListener(errorListener);
        lexer.addErrorListener(errorListener);
        return parser;
    }
}
