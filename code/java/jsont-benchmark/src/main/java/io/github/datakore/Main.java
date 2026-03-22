package io.github.datakore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.marketplace.OrderParserTest;
import io.github.datakore.marketplace.StringifyUtil;
import io.github.datakore.marketplace.entity.Order;
import org.openjdk.jmh.runner.RunnerException;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public Main() throws IOException {
    }

    public static void main(String[] args) throws IOException, RunnerException {
        OrderParserTest orderParserTest = new OrderParserTest();
        orderParserTest.parseOrderRecords(1);
        orderParserTest.parseOrderRecords(10);
        orderParserTest.parseOrderRecords(100);
        orderParserTest.parseOrderRecords(1_000);
        orderParserTest.parseOrderRecords(10_000);
        orderParserTest.parseOrderRecords(100_000);
        orderParserTest.parseOrderRecords(1_000_000);
    }

    StringifyUtil util = new StringifyUtil();
    StreamingJsonTWriter<Order> jsontStringifier = util.createStreamingWriter();
    Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(LocalDate.class, new LocalDateAdapter()).create();

    public void compareStringifySizes(int count) throws IOException {
        AtomicLong counter = new AtomicLong();
        String pathFormat = "jsont-benchmark/target/marketplace_data-%d.%s";
        try (Writer jsonw = Files.newBufferedWriter(Paths.get(String.format(pathFormat, count, "json")));
             Writer jsontw = Files.newBufferedWriter(Paths.get(String.format(pathFormat, count, "jsont")))) {
            List<Order> list = util.createObjectList(count);
            gson.toJson(list, jsonw);
            jsontStringifier.stringify(list, jsontw, true);
        }
    }

    private class LocalDateAdapter extends TypeAdapter<LocalDate> {
        private final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE; // e.g., "2023-10-27"

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(FORMATTER));
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                return LocalDate.parse(in.nextString(), FORMATTER);
            }
        }
    }
}
