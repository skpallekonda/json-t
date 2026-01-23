package io.github.datakore.jsont.util;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ProgressMonitor implements Consumer<StepCounter> {

    private final long batchSize;
    private final long windowSize;
    private final long totalRecords;
    private final boolean reportingAtRecordLevel;
    private Instant start = Instant.now();
    private long startMemory;

    public ProgressMonitor(long totalRecords, long batchSize, long progressWindowSize, boolean reportingAtRecordLevel) {
        this.totalRecords = totalRecords;
        this.batchSize = batchSize;
        this.windowSize = progressWindowSize;
        this.reportingAtRecordLevel = reportingAtRecordLevel;
        this.startMemory = getUsedMemory();
    }

    public ProgressMonitor(long totalRecords, long batchSize, long progressWindowSize) {
        this(totalRecords, batchSize, progressWindowSize, false);
    }

    public void startProgress() {
        this.start = Instant.now();
        this.startMemory = getUsedMemory();
        System.out.printf("Start Memory: %s%n", formatMemory(this.startMemory));
    }

    @Override
    public void accept(StepCounter counter) {
        long currentCount = counter.getCounter();
        long currentBatch;
        long recordsProcessed;

        if (reportingAtRecordLevel) {
            // If reporting at record level, currentCount is the total records processed so far
            recordsProcessed = currentCount;
            currentBatch = currentCount / batchSize;
            
            // Check if we just finished a window of batches
            if (currentCount > 0 && currentCount % (batchSize * windowSize) == 0) {
                 printProgress(counter.getName(), currentBatch, recordsProcessed);
            }
        } else {
            // If reporting at batch level, currentCount is the batch number (1, 2, 3...)
            currentBatch = currentCount;
            recordsProcessed = currentBatch * batchSize;
            
            if (currentBatch > 0 && currentBatch % windowSize == 0) {
                printProgress(counter.getName(), currentBatch, recordsProcessed);
            }
        }
    }

    private void printProgress(String stepName, long batchNo, long recordsProcessed) {
        Instant now = Instant.now();
        long elapsed = Duration.between(start, now).toMillis();
        long throughput = (recordsProcessed * 1000) / Math.max(1, elapsed);
        
        long currentMemory = getUsedMemory();
        long memoryDiff = currentMemory - startMemory;
        String sign = memoryDiff >= 0 ? "+" : "";

        System.out.printf("%s: Batch %d: [%s] %d records, %d rec/sec, Mem: %s (%s%s)%n", 
                DateTimeFormatter.ISO_INSTANT.format(now),
                batchNo, 
                stepName, 
                recordsProcessed, 
                throughput,
                formatMemory(currentMemory),
                sign,
                formatMemory(memoryDiff));
    }

    public Duration endProgress() {
        Duration totalDuration = Duration.between(start, Instant.now());
        long elapsed = totalDuration.toMillis();
        long throughput = (totalRecords * 1000) / Math.max(1, elapsed);
        
        long endMemory = getUsedMemory();
        long memoryDiff = endMemory - startMemory;
        String sign = memoryDiff >= 0 ? "+" : "";

        System.out.printf("Total Records: %d, Duration: %s, Throughput: %d rec/sec, Final Mem: %s (%s%s)%n",
                totalRecords, totalDuration, throughput, formatMemory(endMemory), sign, formatMemory(memoryDiff));
        return totalDuration;
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private String formatMemory(long bytes) {
        long absBytes = Math.abs(bytes);
        if (absBytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(absBytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
