package io.github.datakore.jsont.util;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ProgressMonitor implements Consumer<StepCounter> {

    private final long batchSize;
    private final long windowSize;
    private final long totalRecords;
    private Instant start = Instant.now();

    public ProgressMonitor(long totalRecords, long batchSize, long progressWindowSize) {
        this.totalRecords = totalRecords;
        this.batchSize = batchSize;
        this.windowSize = progressWindowSize;
    }

    public void startProgress() {
        this.start = Instant.now();
    }

    @Override
    public void accept(StepCounter batchNum) {
        if (batchNum.getCounter() % windowSize == 0) {
            Instant now = Instant.now();
            long elapsed = Duration.between(start, now).toMillis();
            long recsProcessed = batchNum.getCounter() * batchSize;
            System.out.printf("%s: Batch %d: [%s] %d records, %d rec/msec%n", DateTimeFormatter.ISO_INSTANT.format(now),
                    batchNum.getCounter(), batchNum.getName(), recsProcessed, recsProcessed / Math.max(1, elapsed));
        }
    }

    public Duration endProgress() {
        Duration totalDuration = Duration.between(start, Instant.now());
        System.out.printf("Total: %s, Throughput: %d rec/sec%n",
                totalDuration, totalRecords / totalDuration.toMillis());
        return totalDuration;
    }
}
