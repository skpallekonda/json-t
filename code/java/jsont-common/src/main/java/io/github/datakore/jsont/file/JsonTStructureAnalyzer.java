package io.github.datakore.jsont.file;

import io.github.datakore.jsont.chunk.AnalysisResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonTStructureAnalyzer {
    private static final Pattern NS_START = Pattern.compile("^\\s*\\{\\s*namespace\\s*:");
    private static final Pattern DATA_BLOCK_START = Pattern.compile("^\\s*\\{\\s*data-schema\\s*:");
    // Regex to find "data-schema: <SchemaName>" and capture the name
    private static final Pattern DATA_SCHEMA_EXTRACTOR = Pattern.compile("data-schema\\s*:\\s*([a-zA-Z0-9_]+)");
    // Regex to find "data :" or "data:" followed by "["
    private static final Pattern DATA_ARRAY_START_PATTERN = Pattern.compile("data\\s*:\\s*\\[");

    /**
     * Reads all bytes from an input stream and returns them as a byte array.
     * This is a Java 8 compatible replacement for InputStream.readAllBytes().
     *
     * @param inputStream the input stream to read from
     * @return a byte array containing all bytes from the stream
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

    public AnalysisResult analyze(Path filePath) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(filePath, StandardOpenOption.READ)) {
            return analyze(channel);
        }
    }

    public AnalysisResult analyze(InputStream inputStream) throws IOException {
        // Read the stream into memory to allow seeking. This is necessary because the analyzer
        // needs to jump back and forth, which a generic InputStream does not support.
        byte[] bytes = readAllBytes(inputStream);
        try (SeekableByteChannel channel = new InMemorySeekableByteChannel(bytes)) {
            return analyze(channel);
        }
    }

    public AnalysisResult analyze(SeekableByteChannel channel) throws IOException {
        // Allocate peek buffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long initialPosition = channel.position();
        channel.read(buffer);
        buffer.flip();

        String peekHeader = StandardCharsets.UTF_8.decode(buffer).toString();
        Matcher nsMatcher = NS_START.matcher(peekHeader);
        Matcher dataMatcher = DATA_BLOCK_START.matcher(peekHeader);

        if (nsMatcher.find()) {
            return analyzeFullDocument(channel, initialPosition + nsMatcher.start());
        } else if (dataMatcher.find()) {
            return analyzeDataBlock(channel, initialPosition + dataMatcher.start(), null);
        } else {
            return new AnalysisResult(AnalysisResult.FileVariant.FRAGMENT, null, null, 0);
        }
    }

    private AnalysisResult analyzeFullDocument(SeekableByteChannel channel, long startOffset) throws IOException {
        BlockRegion nsRegion = readUntilBalancedBrace(channel, startOffset);
        String nsBlock = nsRegion.content;

        // Check for Data Block after Namespace
        long nextStart = nsRegion.endOffset;
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.position(nextStart);
        channel.read(buffer);
        buffer.flip();

        String nextPeek = StandardCharsets.UTF_8.decode(buffer).toString();
        Matcher dataBlockMatcher = DATA_BLOCK_START.matcher(nextPeek);

        if (dataBlockMatcher.find()) {
            long dataBlockStart = nextStart + dataBlockMatcher.start();
            return analyzeDataBlock(channel, dataBlockStart, nsBlock);
        }

        return new AnalysisResult(AnalysisResult.FileVariant.SCHEMA_ONLY, nsBlock, null, -1);
    }

    private AnalysisResult analyzeDataBlock(SeekableByteChannel channel, long startOffset, String existingNamespace) throws IOException {
        DataStartFinder finder = findDataArrayStart(channel, startOffset);
        return new AnalysisResult(
                existingNamespace == null ? AnalysisResult.FileVariant.DATA_BLOCK : AnalysisResult.FileVariant.FULL_DOCUMENT,
                existingNamespace,
                finder.schemaName, // Pass the extracted schema name
                finder.dataArrayStartOffset
        );
    }

    // --- Helpers with inner static classes for results ---

    private static class BlockRegion {
        final String content;
        final long endOffset;

        BlockRegion(String content, long endOffset) {
            this.content = content;
            this.endOffset = endOffset;
        }
    }

    private BlockRegion readUntilBalancedBrace(SeekableByteChannel channel, long startOffset) throws IOException {
        StringBuilder sb = new StringBuilder();
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        channel.position(startOffset);

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        boolean started = false;

        while (channel.read(buffer) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                char c = (char) buffer.get();
                sb.append(c);

                if (inString) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') inString = false;
                    continue;
                }

                if (c == '"') inString = true;
                else if (c == '{') {
                    depth++;
                    started = true;
                } else if (c == '}') {
                    depth--;
                    if (started && depth == 0) {
                        return new BlockRegion(sb.toString(), startOffset + sb.length());
                    }
                }
            }
            buffer.clear();
        }
        throw new IOException("Unexpected EOF: Block not closed.");
    }

    private static class DataStartFinder {
        final String schemaName;
        final long dataArrayStartOffset;

        DataStartFinder(String schemaName, long o) {
            this.schemaName = schemaName;
            this.dataArrayStartOffset = o;
        }
    }

    private DataStartFinder findDataArrayStart(SeekableByteChannel channel, long startOffset) throws IOException {
        StringBuilder sb = new StringBuilder();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.position(startOffset);

        while (channel.read(buffer) > 0) {
            buffer.flip();
            sb.append(StandardCharsets.UTF_8.decode(buffer));
            String currentStr = sb.toString();

            // Use Regex to find "data : [" specifically, avoiding "data-schema"
            Matcher matcher = DATA_ARRAY_START_PATTERN.matcher(currentStr);
            if (matcher.find()) {
                int dataStartIdx = matcher.start();
                int bracketIdx = matcher.end() - 1; // The last char matched is '['

                // Extract schema name from the header part (before "data : [")
                String header = currentStr.substring(0, dataStartIdx);
                Matcher schemaMatcher = DATA_SCHEMA_EXTRACTOR.matcher(header);
                String schemaName = null;
                if (schemaMatcher.find()) {
                    schemaName = schemaMatcher.group(1);
                }

                long absoluteOffset = startOffset + bracketIdx + 1; // +1 for '['
                return new DataStartFinder(schemaName, absoluteOffset);
            }

            buffer.clear();
            if (sb.length() > 100_000) throw new IOException("Header too large");
        }
        throw new IOException("Could not find 'data: ['");
    }
}
