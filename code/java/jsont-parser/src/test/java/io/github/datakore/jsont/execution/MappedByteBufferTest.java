package io.github.datakore.jsont.execution;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class MappedByteBufferTest {

    @Test
    void testMappedByteBuffer() throws IOException {
        Path pathToRead = Paths.get("src/test/resources/test-file-with-namespace.jsont");
        try (FileChannel channel = (FileChannel) Files.newByteChannel(pathToRead, StandardOpenOption.READ)) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 654, (946 - 654) + 1);
            if (buffer != null) {
                CharBuffer charBuffer = Charset.forName("UTF-8").decode(buffer);
                System.out.println(charBuffer.toString());
            }
        }
    }


}
