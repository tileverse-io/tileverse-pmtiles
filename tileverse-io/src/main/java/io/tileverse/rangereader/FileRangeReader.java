package io.tileverse.rangereader;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * A thread-safe file-based implementation of RangeReader.
 * <p>
 * This implementation uses a FileChannel with position-based reads to safely handle
 * concurrent access from multiple threads without interference.
 */
public class FileRangeReader extends AbstractRangeReader implements RangeReader {

    private final FileChannel channel;

    /**
     * Creates a new FileRangeReader for the specified file.
     *
     * @param path The path to the file
     * @throws IOException If an I/O error occurs
     */
    public FileRangeReader(Path path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
    }

    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) throws IOException {

        // set limit to read at most actualLength
        target.limit(target.position() + actualLength);

        // Read until buffer is full or end of file
        int totalRead = 0;
        long currentPosition = offset;

        while (totalRead < actualLength) {
            // Use the position-based read method for thread safety
            // This allows concurrent reads without interference
            int read = channel.read(target, currentPosition);
            if (read == -1) {
                // End of file reached
                break;
            }
            totalRead += read;
            currentPosition += read;
        }
        // Return the actual number of bytes read
        return totalRead;
    }

    @Override
    public long size() throws IOException {
        // size() is thread-safe in FileChannel
        return channel.size();
    }

    @Override
    public void close() throws IOException {
        // close() is thread-safe in FileChannel
        channel.close();
    }

    /**
     * Creates a new builder for FileRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for FileRangeReader.
     */
    public static class Builder {
        private Path path;

        private Builder() {}

        /**
         * Sets the file path.
         *
         * @param path the file path
         * @return this builder
         */
        public Builder path(Path path) {
            this.path = Objects.requireNonNull(path, "Path cannot be null");
            return this;
        }

        /**
         * Sets the file path from a string.
         *
         * @param pathString the file path as a string
         * @return this builder
         */
        public Builder path(String pathString) {
            Objects.requireNonNull(pathString, "Path string cannot be null");
            this.path = Paths.get(pathString);
            return this;
        }

        /**
         * Sets the file path from a URI.
         *
         * @param uri the file URI
         * @return this builder
         */
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri, "URI cannot be null");
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("URI must have file scheme: " + uri);
            }
            this.path = Paths.get(uri);
            return this;
        }

        /**
         * Builds the FileRangeReader.
         *
         * @return a new FileRangeReader instance
         * @throws IOException if an error occurs during construction
         */
        public FileRangeReader build() throws IOException {
            if (path == null) {
                throw new IllegalStateException("Path must be set");
            }

            return new FileRangeReader(path);
        }
    }
}
