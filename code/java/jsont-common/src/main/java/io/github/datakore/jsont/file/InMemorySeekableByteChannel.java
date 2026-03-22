package io.github.datakore.jsont.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

class InMemorySeekableByteChannel implements SeekableByteChannel {
    private final byte[] data;
    private long position;
    private boolean open = true;

    public InMemorySeekableByteChannel(byte[] data) {
        this.data = data;
        this.position = 0;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (!open) throw new IOException("Channel closed");
        if (position >= data.length) return -1;

        int remaining = (int) (data.length - position);
        int toRead = Math.min(dst.remaining(), remaining);
        dst.put(data, (int) position, toRead);
        position += toRead;
        return toRead;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException("Read-only");
    }

    @Override
    public long position() throws IOException {
        if (!open) throw new IOException("Channel closed");
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        if (!open) throw new IOException("Channel closed");
        if (newPosition < 0 || newPosition > data.length) throw new IOException("Invalid position");
        this.position = newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        if (!open) throw new IOException("Channel closed");
        return data.length;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new UnsupportedOperationException("Read-only");
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }
}
