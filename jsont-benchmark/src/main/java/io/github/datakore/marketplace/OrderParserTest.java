package io.github.datakore.marketplace;

import io.github.datakore.jsont.util.ProgressMonitor;
import io.github.datakore.marketplace.entity.Order;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public class OrderParserTest {

    StringifyUtil util = new StringifyUtil();

    public OrderParserTest() throws IOException {
    }

    public void parseOrderRecords(long recordCount) throws IOException {
        Path dataFile = util.setupTestFileFor(recordCount);
        long batchSize = Math.max(recordCount / 100, 5);
        long flushRecords = 10;
        final AtomicLong counter = new AtomicLong();
        String reportFile = String.format("jsont-benchmark/target/marketplace_report-%s.txt", recordCount);
        try (Writer writer = new FileWriter(reportFile)) {
            ProgressMonitor monitor = new ProgressMonitor(recordCount, batchSize, flushRecords, true, writer);
            util.getJsonTConfig().withMonitor(monitor).source(dataFile).convert(Order.class, 4)
                    .doOnNext(order -> counter.incrementAndGet())
                    .blockLast();
            monitor.endProgress();
        }
    }
}
