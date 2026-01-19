package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.JsonTLexer;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.listener.JsonTErrorListener;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.io.InputStream;

public final class ParserUtil {

    public static JsonTParser createParser(
            InputStream input, ErrorCollector errorCollector,
            ParseTreeListener listener) {
        JsonTErrorListener errorListener = new JsonTErrorListener(errorCollector);
        // This reads from disk as needed, avoiding the 2GB array limit for CharStream
        UnbufferedCharStream inputCharStream = new UnbufferedCharStream(input);
        JsonTLexer lexer = new JsonTLexer(inputCharStream);
        // Standard CommonTokenStream tries to load ALL tokens into a List
        // CommonTokenStream tokens = new CommonTokenStream(lexer);
        lexer.setTokenFactory(new CommonTokenFactory(true)); // Optimize for streaming
        // UnbufferedTokenStream keeps only what it needs for lookahead
        TokenStream tokens = new UnbufferedTokenStream<>(lexer);
        JsonTParser parser = new JsonTParser(tokens);
        parser.setBuildParseTree(false);
        ANTLRErrorStrategy handler = new BailErrorStrategy();
        parser.setErrorHandler(handler);
        lexer.removeErrorListeners();
        parser.removeParseListeners();
        parser.removeErrorListeners();

        lexer.addErrorListener(errorListener);
        parser.addParseListener(listener);
        parser.addErrorListener(errorListener);
        return parser;
    }
}
