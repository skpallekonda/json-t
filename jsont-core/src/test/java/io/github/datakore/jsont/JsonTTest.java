package io.github.datakore.jsont;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import io.github.datakore.jsont.core.JsonTContext;
import io.github.datakore.jsont.entity.Address;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonTTest {

    //String schemaPath = "src/test/resources/schema.jsont";
    String schemaPath = "src/test/resources/schema.jsont";
    // String dataPath = "src/test/resources/data.jsont";
    String dataPath = "500000-1767884033236.jsont";


    Path scPath = Paths.get(schemaPath);
    Path datPath = Paths.get(dataPath);

    int total = 500000;

    @Test
    void shouldReadDataAsList() throws IOException {
        JsonTContext ctx = JsonT.builder()
                .withAdapter(new AddressAdapter()).withAdapter(new UserAdapter())
                .withErrorCollector(new DefaultErrorCollector()).parseCatalog(scPath);

        CharStream dataStream = CharStreams.fromPath(datPath);
        List<User> userList = ctx.withData(dataStream).as(User.class).list();

        assertEquals(total, userList.size());
        System.out.println(userList);
    }

    @Test
    void shouldReadDataAsStream() throws IOException {
        JsonTContext ctx = JsonT.builder()
                .withAdapter(new AddressAdapter()).withAdapter(new UserAdapter())
                .withErrorCollector(new DefaultErrorCollector()).parseCatalog(scPath);

        CharStream dataStream = CharStreams.fromPath(datPath);
        Flux<User> userList = ctx.withData(dataStream).as(User.class).stream();

        assertEquals(total, userList.toStream().count());
    }

    @Test
    void shouldGenerateJsonTString() throws IOException {
        JsonTContext ctx = JsonT.builder()
                .withAdapter(new AddressAdapter()).withAdapter(new UserAdapter())
                .withErrorCollector(new DefaultErrorCollector())
                .parseCatalog(scPath);
//        System.out.println(ctx.stringify(User.class));
        List<User> users = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            String indexChar = String.valueOf(i);
            User user = new User();
            user.setId(123456 + i);
            user.setUserName("sasikp".concat(indexChar));
            user.setEmail(indexChar.concat("test@sasikp.com"));
            Address address = new Address();
            address.setCity("Chennai".concat(indexChar));
            address.setStreet("34a Perumbakkam".concat(indexChar));
            int add = i % 100;
            String adds = String.valueOf(add);
            address.setZipCode("60015".concat(adds));
            user.setAddress(address);
            user.setTags(new String[]{"developer".concat(adds), "admin".concat(adds)});
            users.add(user);
        }
        String out = ctx.stringify(users);
        String filename = String.format("%d-%d.jsont", total, System.currentTimeMillis());
        Files.writeString(Paths.get(filename), out);
    }
}
