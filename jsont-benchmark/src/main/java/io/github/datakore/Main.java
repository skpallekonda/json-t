package io.github.datakore;

import io.github.datakore.jsont.benchmark.JsonTBenchmark;
import io.github.datakore.marketplace.StringifyUtil;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, RunnerException {
        System.out.println("Starting JsonT JMH Benchmark...");
//        StringifyUtil stringifyUtil = new StringifyUtil();
//        // long[] recordCounts = {100000, 500000, 1_000_000, 2_000_000, 5_000_000, 10_000_000};
//        long[] recordCounts = {1_000_000, 2_000_000};
//        for (int i = 0; i < recordCounts.length; i++) {
//            stringifyUtil.setupTestFileFor(recordCounts[i]);
//        }

        Options opt = new OptionsBuilder()
                .include(JsonTBenchmark.class.getSimpleName())
                .forks(1)
                .jvmArgs(
                        "-Xms4G",
                        "-Xmx4G",
                        "-XX:+UseZGC",
                        "-XX:+ZGenerational"
                )
                .build();

        new Runner(opt).run();
    }
}
