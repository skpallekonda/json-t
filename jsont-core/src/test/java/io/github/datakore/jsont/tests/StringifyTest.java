package io.github.datakore.jsont.tests;

import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.datagen.AllTypeEntryGenerator;
import io.github.datakore.jsont.datagen.UserGenerator;
import io.github.datakore.jsont.entity.AllTypeHolder;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.util.ProgressMonitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

public class StringifyTest extends BaseTests {

    private JsonTConfig jsonTConfig;

    @Test
    void shouldLoadUserSchema() {
        String schemaPath = "src/test/resources/ns-schema.jsont";
        jsonTConfig = getJsonTConfig(schemaPath);
        Assertions.assertNotNull(jsonTConfig);
        Assertions.assertNotNull(jsonTConfig.getNamespace());
        Assertions.assertNotNull(jsonTConfig.getNamespace().findSchema("User"));
        Assertions.assertNotNull(jsonTConfig.getNamespace().findSchema("Address"));
    }

    @Test
    void shouldStringifyAUser() throws IOException {
        String schemaPath = "src/test/resources/ns-schema.jsont";
        UserGenerator userGenerator = new UserGenerator();
        User user = userGenerator.generate("User");
        StreamingJsonTWriter<User> stringifier = getTypedStreamWriter(schemaPath, User.class, userGenerator, loadUserAdapters());
        StringWriter sw = new StringWriter();
        stringifier.stringify(user, sw, false);
        System.out.println(sw);
    }

    @Test
    void shouldStringifyMultipleUsers() throws IOException {
        String schemaPath = "src/test/resources/ns-schema.jsont";
        UserGenerator userGenerator = new UserGenerator();
        userGenerator.initialize();
        StreamingJsonTWriter<User> stringifier = getTypedStreamWriter(schemaPath, User.class, userGenerator, loadUserAdapters());
        StringWriter sw = new StringWriter();
        long totalRecords = 100;
        int batchSize = 10;
        int flushSize = 5;
        ProgressMonitor monitor = new ProgressMonitor(totalRecords, batchSize, flushSize,sw);
        monitor.startProgress();
        stringifier.stringify(sw, totalRecords, batchSize, flushSize, false, monitor);
        monitor.endProgress();
        System.out.println(sw);
    }

    @Test
    void shouldStringifyAllTypeDataList() throws Exception {
        String schemaPath = "src/test/resources/all-type-schema.jsont";
        AllTypeEntryGenerator generator = new AllTypeEntryGenerator();
        generator.initialize();
        StreamingJsonTWriter<AllTypeHolder> stringifier = getTypedStreamWriter(schemaPath, AllTypeHolder.class, generator, loadAllTypeAdapters());
        StringWriter sw = new StringWriter();
        long totalRecords = 2;
        int batchSize = 1;
        int flushSize = 5;
        ProgressMonitor monitor = new ProgressMonitor(totalRecords, batchSize, flushSize,sw);
        monitor.startProgress();
        stringifier.stringify(sw, totalRecords, batchSize, flushSize, false, monitor);
        monitor.endProgress();
        System.out.println(sw);
    }


}
