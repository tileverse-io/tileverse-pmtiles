package io.tileverse.rangereader;

import java.io.IOException;
import java.nio.ByteBuffer;

abstract class AbstractRangeReader implements RangeReader {

    @Override
    public final int readRange(long offset, int length, ByteBuffer target) throws IOException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target buffer cannot be null");
        }
        if (target.isReadOnly()) {
            throw new java.nio.ReadOnlyBufferException();
        }

        if (length == 0) {
            // For zero-length reads, return 0 bytes read
            target.limit(target.position());
            return 0;
        }

        // Check if target has enough remaining space
        if (target.remaining() < length) {
            throw new IllegalArgumentException(
                    "Target buffer has insufficient remaining capacity: " + target.remaining() + " < " + length);
        }

        final long fileSize = size();
        if (offset >= fileSize) {
            // Offset is beyond EOF, return 0 bytes read
            target.limit(target.position());
            return 0;
        }

        int actualLength = length;
        if (offset + length > fileSize) {
            // Read extends beyond EOF, truncate it
            actualLength = (int) (fileSize - offset);
        }

        final int initialPosition = target.position();

        final int readCount = readRangeNoFlip(offset, actualLength, target);

        // Prepare the buffer for reading by flipping it within the read range
        int newPosition = target.position();
        target.position(initialPosition);
        target.limit(newPosition);
        return readCount;
    }

    protected abstract int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) throws IOException;
}
