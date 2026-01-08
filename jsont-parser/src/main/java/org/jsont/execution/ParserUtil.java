package org.jsont.execution;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jsont.errors.collector.ErrorCollector;
import org.jsont.grammar.JsonTLexer;
import org.jsont.grammar.JsonTParser;
import org.jsont.listener.DataListener;
import org.jsont.listener.JsonTErrorListener;

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
