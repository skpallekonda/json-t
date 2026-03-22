package io.github.datakore.jsont.chunk;

import io.github.datakore.jsont.util.ChunkContext;

public class DataRowRecord {
    private final long rowIndex;
    private final byte[] data;
    private final ChunkContext context;


    public DataRowRecord(long rowIndex, byte[] data, ChunkContext context) {
        this.rowIndex = rowIndex;
        this.data = data;
        this.context = context;
    }

    public long getRowIndex() { return rowIndex; }
    public byte[] getData() { return data; }
    public ChunkContext getContext() { return context; }

}
