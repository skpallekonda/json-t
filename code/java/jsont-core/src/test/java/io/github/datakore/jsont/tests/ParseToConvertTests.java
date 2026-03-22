package io.github.datakore.jsont.tests;

import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.datagen.UserGenerator;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.util.ProgressMonitor;
import io.github.datakore.jsont.util.StepCounter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

public class ParseToConvertTests extends BaseTests {

    Path generateUserData(long recordCount, boolean includeSchema) throws IOException {
        String schemaPath = "src/test/resources/ns-schema.jsont";
        UserGenerator userGenerator = new UserGenerator();
        userGenerator.initialize();
        StreamingJsonTWriter<User> stringifier = getTypedStreamWriter(schemaPath, User.class, userGenerator, loadUserAdapters());
        Writer writer;
        Path temp = null;
        String path = String.format("target/user-data_%d.jsont", recordCount);
        temp = Files.createFile(Paths.get(path));
        writer = Files.newBufferedWriter(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        long batchSize = Math.max(recordCount / 100, 10);
        long flushSize = Math.min(5, batchSize / 10);
        StringWriter stringWriter = new StringWriter();
        ProgressMonitor monitor = new ProgressMonitor(recordCount, batchSize, flushSize, stringWriter);
        monitor.startProgress();
        stringifier.stringify(writer, recordCount, (int) batchSize, (int) flushSize, includeSchema, monitor);
        monitor.endProgress();
        return temp.toAbsolutePath();
    }

    @Test
    void shouldParseUserData() throws IOException {
        jsonTConfig = JsonT.configureBuilder().withAdapters(loadUserAdapters()).withErrorCollector(errorCollector).build();
        try {
            Path path = generateUserData(1, true);
            jsonTConfig.source(path).parse(4)
                    .doOnNext(
                            rowNode -> System.out.println(rowNode.values())
                    ).blockLast();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldConvertUserData() throws IOException {
        jsonTConfig = JsonT.configureBuilder().withAdapters(loadUserAdapters()).withErrorCollector(errorCollector).build();
        try {
            Path path = generateUserData(1, true);
            jsonTConfig.source(path).convert(User.class, 4)
                    .doOnNext(
                            System.out::println
                    ).blockLast();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldConvertManyUserData() throws IOException {
        jsonTConfig = JsonT.configureBuilder().withAdapters(loadUserAdapters()).withErrorCollector(errorCollector).build();
        SecureRandom random = new SecureRandom();
        try {
            long count = 1_000_000;
            Path path = generateUserData(count, true);
            StringWriter stringWriter = new StringWriter();
            ProgressMonitor monitor = new ProgressMonitor(count, 10_000, 50, true, stringWriter);
            AtomicLong counter = new AtomicLong(0);
            monitor.startProgress();
            jsonTConfig.withMonitor(monitor).source(path).convert(User.class, 4)
                    .doOnNext(
                            user -> monitor.accept(new StepCounter("client", counter.incrementAndGet()))
                    ).blockLast();
            monitor.endProgress();
            System.out.println(stringWriter.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
