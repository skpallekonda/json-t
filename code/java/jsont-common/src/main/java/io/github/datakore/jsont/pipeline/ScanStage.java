package io.github.datakore.jsont.pipeline;

import io.github.datakore.jsont.chunk.DataRowRecord;
import io.github.datakore.jsont.file.DataRowBuffer;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.StepCounter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public class ScanStage implements PipelineStage<Void, DataRowRecord> {

    private final InputStream inputStream;
    private final Consumer<StepCounter> monitor;
    private final ChunkContext chunkContext;


    public ScanStage(InputStream inputStream, ChunkContext chunkContext, Consumer<StepCounter> monitor) {
        assert inputStream != null;
        this.inputStream = inputStream;
        assert chunkContext != null;
        assert chunkContext.getNamespace() != null;
        assert chunkContext.getDataSchema() != null;
        this.chunkContext = chunkContext;
        this.monitor = monitor;
    }

    @Override
    public Flux<DataRowRecord> execute(Flux<Void> input) {
        return Flux.using(
                () -> {
                    try {
                        return new DataRowBuffer(inputStream, chunkContext);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialize DataRowBuffer", e);
                    }
                },
                buffer -> Flux.generate(
                        (SynchronousSink<DataRowRecord> sink) -> {
                            try {
                                DataRowRecord record = buffer.next();
                                if (record != null) {
                                    sink.next(record);
                                    monitor(monitor, "scan", record.getRowIndex());
                                } else {
                                    sink.complete();
                                }
                            } catch (IOException e) {
                                sink.error(e);
                            }
                        })
                , buffer -> {
                    try {
                        buffer.close();
                    } catch (IOException e) {
                        // Log but don't throw
                    }
                }
        );
    }
}
