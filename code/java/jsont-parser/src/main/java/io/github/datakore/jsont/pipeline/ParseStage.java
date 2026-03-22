package io.github.datakore.jsont.pipeline;

import io.github.datakore.jsont.chunk.DataRowRecord;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.execution.RowNodeCaptureDataPipeline;
import io.github.datakore.jsont.grammar.JsonTLexer;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.parser.DataRowVisitor;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.StepCounter;
import org.antlr.v4.runtime.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ParseStage implements PipelineStage<DataRowRecord, RowNode> {

    private final ErrorCollector errorCollector;
    private final Consumer<StepCounter> monitor;
    private final int parallelism;
    private final ChunkContext chunkContext;

    private final ThreadLocal<ParserContext> parserThreadLocal = ThreadLocal.withInitial(() -> {
        ByteArrayInputStream dummy = new ByteArrayInputStream(new byte[0]);
        UnbufferedCharStream charStream = new UnbufferedCharStream(dummy);
        JsonTLexer lexer = new JsonTLexer(charStream);
        lexer.setTokenFactory(new CommonTokenFactory(true)); // Optimize for streaming
        TokenStream tokens = new UnbufferedTokenStream<>(lexer);
        JsonTParser parser = new JsonTParser(tokens);
        parser.setBuildParseTree(false);
        ANTLRErrorStrategy handler = new BailErrorStrategy();
        parser.setErrorHandler(handler);
        lexer.removeErrorListeners();
        parser.removeParseListeners();
        parser.removeErrorListeners();
        return new ParserContext(parser, lexer, charStream);
    });

    public ParseStage(ErrorCollector errorCollector, ChunkContext chunkContext, Consumer<StepCounter> monitor, int parallelism) {
        this.errorCollector = errorCollector;
        this.chunkContext = chunkContext;
        this.monitor = monitor;
        this.parallelism = parallelism;
    }

    @Override
    public Flux<RowNode> execute(Flux<DataRowRecord> input) {
        AtomicLong counter = new AtomicLong();

        ParallelFlux<RowNode> parallelFlux = input
                .parallel(parallelism)
                .runOn(Schedulers.parallel())
                .map(this::parseRecord)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .doOnNext(
                        row -> {
                            monitor(monitor, "parse", counter.incrementAndGet());
                        });

        return parallelFlux.sequential();
    }

    private Optional<RowNode> parseRecord(DataRowRecord record) {
        RowNodeCaptureDataPipeline capture = new RowNodeCaptureDataPipeline();
        DataRowVisitor visitor = new DataRowVisitor(errorCollector, chunkContext, capture);
        ParserContext ctx = parserThreadLocal.get();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(record.getData())) {
            resetContext(ctx, bais, visitor);
            ctx.parser.dataRow();
        } catch (IOException e) {
            throw new DataException("Failed to parse record", e);
        }
        if (capture.hasError()) {
            throw new DataException("Error parsing record: " + capture.getError());
        }
        if (capture.getResult() != null) {
            capture.getResult().setIndex(record.getRowIndex());
            return Optional.of(capture.getResult());
        } else {
            return Optional.empty();
        }
    }

    private void resetContext(ParserContext ctx, ByteArrayInputStream bais, DataRowVisitor visitor) {
        // Reset input stream only
        // ctx.charStream.
        ctx.charStream = new UnbufferedCharStream(bais);
        ctx.lexer.setInputStream(ctx.charStream);
        ctx.parser.setInputStream(new UnbufferedTokenStream<>(ctx.lexer));

        ctx.parser.reset();

        // Set visitor
        ctx.parser.removeParseListeners();
        ctx.parser.addParseListener(visitor);
    }

    private static class ParserContext {
        JsonTParser parser;
        JsonTLexer lexer;
        UnbufferedCharStream charStream;

        ParserContext(JsonTParser parser, JsonTLexer lexer, UnbufferedCharStream charStream) {
            this.parser = parser;
            this.lexer = lexer;
            this.charStream = charStream;
        }
    }
}
