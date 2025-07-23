package io.tileverse.rangereader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for combining BlockAlignedRangeReader with CachingRangeReader.
 */
class BlockAlignedCachingTest {

    @TempDir
    Path tempDir;

    private Path testFile;
    private String textContent;
    private FileRangeReader fileReader;
    private CountingRangeReader countingReader;
    private BlockAlignedRangeReader blockAlignedReader;
    private CachingRangeReader cachingReader;

    // Use a small block size for testing
    private static final int TEST_BLOCK_SIZE = 16;

    @BeforeEach
    void setUp() throws IOException {
        // Create a test file with known content
        testFile = tempDir.resolve("block-cache-test.txt");
        textContent = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Files.writeString(testFile, textContent, StandardOpenOption.CREATE);

        // Initialize the readers - this shows the decorator pattern in action
        fileReader = new FileRangeReader(testFile);
        countingReader = new CountingRangeReader(fileReader);
        blockAlignedReader = new BlockAlignedRangeReader(countingReader, TEST_BLOCK_SIZE);
        cachingReader = new CachingRangeReader(blockAlignedReader);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (fileReader != null) {
            fileReader.close();
        }
    }

    @Test
    void testCachingOfBlockAlignedReads() throws IOException {
        // Read a range that's not aligned to a block boundary
        ByteBuffer buffer = cachingReader.readRange(10, 10);
        assertEquals(10, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        assertEquals(textContent.substring(10, 20), new String(bytes, StandardCharsets.UTF_8));

        // The BlockAlignedRangeReader should have read the full blocks containing the range
        // which means the CountingRangeReader should have been called once or twice
        // (depending on whether the range crosses a block boundary)
        int initialCount = countingReader.getReadCount();
        assertTrue(initialCount == 1 || initialCount == 2, "Expected 1 or 2 reads, but got " + initialCount);

        // Read the same range again - it should come from cache
        ByteBuffer buffer2 = cachingReader.readRange(10, 10);
        assertEquals(10, buffer2.remaining());

        byte[] bytes2 = new byte[buffer2.remaining()];
        buffer2.get(bytes2);
        assertEquals(textContent.substring(10, 20), new String(bytes2, StandardCharsets.UTF_8));

        // CountingRangeReader should not have been called again
        assertEquals(initialCount, countingReader.getReadCount());
    }

    @Test
    void testCachingEfficiencyWithIdenticalReads() throws IOException {
        // Initial state
        assertEquals(0, countingReader.getReadCount());

        // First read: should result in BlockAlignedRangeReader accessing the underlying file
        cachingReader.readRange(0, 5);
        int firstReadCount = countingReader.getReadCount();
        assertTrue(firstReadCount > 0, "Expected at least one read");

        // Same read again: should come from cache
        cachingReader.readRange(0, 5);
        assertEquals(firstReadCount, countingReader.getReadCount(), "Should not have read from file again");

        // Different read: will hit the file again
        cachingReader.readRange(10, 5);
        int secondReadCount = countingReader.getReadCount();
        assertTrue(secondReadCount > firstReadCount, "Expected additional reads for new range");

        // Repeat the second read: should come from cache
        cachingReader.readRange(10, 5);
        assertEquals(secondReadCount, countingReader.getReadCount(), "Should not have read from file again");

        // Another new read
        cachingReader.readRange(20, 10);
        int thirdReadCount = countingReader.getReadCount();
        assertTrue(thirdReadCount > secondReadCount, "Expected additional reads for new range");

        // Repeat third read: should come from cache
        cachingReader.readRange(20, 10);
        assertEquals(thirdReadCount, countingReader.getReadCount(), "Should not have read from file again");
    }

    /**
     * A RangeReader that counts the number of reads for testing purposes.
     */
    private static class CountingRangeReader implements RangeReader {
        private final RangeReader delegate;
        private int readCount = 0;

        public CountingRangeReader(RangeReader delegate) {
            this.delegate = delegate;
        }

        @Override
        public ByteBuffer readRange(long offset, int length) throws IOException {
            readCount++;
            return delegate.readRange(offset, length);
        }

        @Override
        public int readRange(long offset, int length, ByteBuffer target) throws IOException {
            readCount++;
            return delegate.readRange(offset, length, target);
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        public int getReadCount() {
            return readCount;
        }
    }
}
