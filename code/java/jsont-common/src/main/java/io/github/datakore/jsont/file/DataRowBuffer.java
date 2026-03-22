package io.github.datakore.jsont.file;

import io.github.datakore.jsont.chunk.DataRowRecord;
import io.github.datakore.jsont.util.ChunkContext;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DataRowBuffer implements AutoCloseable {

    private static final int BUFFER_SIZE = 8192 * 4; // 32KB
    private static final byte L_BRACE = '{';
    private static final byte R_BRACE = '}';
    private static final byte QUOTE = '"';
    private static final byte ESCAPE = '\\';
    private static final byte BRACKET_CLOSE = ']';

    private final InputStream inputStream;
    private final byte[] buffer;
    private int bufferLimit = 0;
    private int bufferPos = 0;
    private long recordIndex = 0;
    private boolean eof = false;
    private final ChunkContext context;

    // State for row scanning
    private int depth = 0;
    private boolean inString = false;
    private boolean escaped = false;
    private final ByteArrayOutputStream currentRow = new ByteArrayOutputStream(1024); // Make non-final to allow nulling
    private boolean insideRow = false;

    public DataRowBuffer(InputStream inputStream, ChunkContext context) throws IOException {
        if (inputStream instanceof BufferedInputStream) {
            this.inputStream = inputStream;
        } else {
            this.inputStream = new BufferedInputStream(inputStream);
        }
        this.buffer = new byte[BUFFER_SIZE];
        this.context = context;
        long skipBytes = context.getDataStartOffset() - 1;
        if (skipBytes > 0) {
            long skipped = this.inputStream.skip(skipBytes);
            if (skipped < skipBytes) {
                long remaining = skipBytes - skipped;
                while (remaining > 0) {
                    long s = this.inputStream.read(new byte[(int) Math.min(remaining, 2048)]);
                    if (s == -1) break;
                    remaining -= s;
                }
            }
        }
    }

    public boolean hasRemaining() throws IOException {
        if (bufferPos < bufferLimit) {
            return true;
        }
        if (eof) {
            return false;
        }
        bufferPos = 0;
        bufferLimit = inputStream.read(buffer);
        if (bufferLimit == -1) {
            eof = true;
            return false;
        }
        return true;
    }

    public DataRowRecord next() throws IOException {
        while (hasRemaining()) {
            byte b = buffer[bufferPos++];

            if (inString) {
                if (escaped) escaped = false;
                else if (b == ESCAPE) escaped = true;
                else if (b == QUOTE) inString = false;

                if (insideRow) {
                    currentRow.write(b);
                }
                continue;
            }

            if (depth == 0 && b == BRACKET_CLOSE) {
                eof = true;
                return null;
            }

            if (b == QUOTE) {
                inString = true;
            } else if (b == L_BRACE) {
                if (depth == 0) {
                    insideRow = true;
                    currentRow.reset();
                }
                depth++;
            }

            if (insideRow) {
                currentRow.write(b);
            }

            if (b == R_BRACE) {
                depth--;
                if (depth == 0 && insideRow) {
                    insideRow = false;
                    byte[] rowData = currentRow.toByteArray();
                    return new DataRowRecord(++recordIndex, rowData, context);
                }
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (currentRow != null) {
            currentRow.reset();
        }
    }
}
