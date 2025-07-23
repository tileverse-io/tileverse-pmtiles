package io.tileverse.rangereader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
 * Tests for BlockAlignedRangeReader.
 */
class BlockAlignedRangeReaderTest {

    @TempDir
    Path tempDir;

    private Path testFile;
    private String textContent;
    private FileRangeReader fileReader;
    private BlockAlignedRangeReader reader;

    // Use a small block size for testing
    private static final int TEST_BLOCK_SIZE = 16;

    @BeforeEach
    void setUp() throws IOException {
        // Create a test file with known content
        testFile = tempDir.resolve("block-test.txt");
        textContent = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Files.writeString(testFile, textContent, StandardOpenOption.CREATE);

        // Initialize the readers
        fileReader = new FileRangeReader(testFile);
        reader = new BlockAlignedRangeReader(fileReader, TEST_BLOCK_SIZE);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (fileReader != null) {
            fileReader.close();
        }
    }

    @Test
    void testConstructorWithNegativeBlockSize() {
        assertThrows(IllegalArgumentException.class, () -> new BlockAlignedRangeReader(fileReader, -1));
    }

    @Test
    void testConstructorWithZeroBlockSize() {
        assertThrows(IllegalArgumentException.class, () -> new BlockAlignedRangeReader(fileReader, 0));
    }

    @Test
    void testConstructorWithNonPowerOfTwoBlockSize() {
        assertThrows(IllegalArgumentException.class, () -> new BlockAlignedRangeReader(fileReader, 15));
    }

    @Test
    void testReadAlignedRange() throws IOException {
        // Read a range that's already aligned to block boundaries
        ByteBuffer buffer = reader.readRange(16, 16);

        assertEquals(16, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(16, 32), result);
    }

    @Test
    void testReadUnalignedOffset() throws IOException {
        // Read from an offset that's not aligned
        ByteBuffer buffer = reader.readRange(10, 10);

        assertEquals(10, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(10, 20), result);
    }

    @Test
    void testReadAcrossMultipleBlocks() throws IOException {
        // Read a range that spans multiple blocks
        ByteBuffer buffer = reader.readRange(10, 30);

        assertEquals(30, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(10, 40), result);
    }

    @Test
    void testReadPartialBlock() throws IOException {
        // Read a small part from the middle of a block
        ByteBuffer buffer = reader.readRange(20, 5);

        assertEquals(5, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(20, 25), result);
    }

    @Test
    void testReadBeyondEnd() throws IOException {
        // Try to read beyond the end of the file
        ByteBuffer buffer = reader.readRange(textContent.length() - 5, 10);

        assertEquals(5, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(textContent.length() - 5), result);
    }

    @Test
    void testReadStartingBeyondEnd() throws IOException {
        // Try to read from an offset beyond the end of the file
        ByteBuffer buffer = reader.readRange(textContent.length() + 10, 5);

        assertEquals(0, buffer.remaining());
    }

    @Test
    void testReadWithZeroLength() throws IOException {
        // Try to read with a length of 0
        ByteBuffer buffer = reader.readRange(10, 0);

        assertEquals(0, buffer.remaining());
    }

    @Test
    void testReadWithNegativeOffset() {
        // Try to read with a negative offset
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(-1, 10));
    }

    @Test
    void testReadWithNegativeLength() {
        // Try to read with a negative length
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(0, -1));
    }

    @Test
    void testDefaultBlockSize() throws IOException {
        // Check that the default constructor sets the correct block size
        try (BlockAlignedRangeReader defaultReader = new BlockAlignedRangeReader(fileReader)) {
            assertEquals(BlockAlignedRangeReader.DEFAULT_BLOCK_SIZE, defaultReader.getBlockSize());
        }
    }

    @Test
    void testGetBlockSize() {
        // Check that getBlockSize returns the correct value
        assertEquals(TEST_BLOCK_SIZE, reader.getBlockSize());
    }

    @Test
    void testDelegateSize() throws IOException {
        // Check that size() delegates to the underlying reader
        assertEquals(textContent.length(), reader.size());
    }

    @Test
    void testReadAlignedRangeWithExplicitBuffer() throws IOException {
        // Read a range that's already aligned to block boundaries with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(16);
        int bytesRead = reader.readRange(16, 16, buffer);

        // Verify returned byte count
        assertEquals(16, bytesRead);

        // Verify buffer is ready for reading
        assertEquals(0, buffer.position());
        assertEquals(16, buffer.limit());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(16, 32), result);
    }

    @Test
    void testReadUnalignedOffsetWithExplicitBuffer() throws IOException {
        // Read from an offset that's not aligned with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(10);
        int bytesRead = reader.readRange(10, 10, buffer);

        // Verify returned byte count
        assertEquals(10, bytesRead);

        // Verify buffer is ready for reading
        assertEquals(0, buffer.position());
        assertEquals(10, buffer.limit());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(10, 20), result);
    }

    @Test
    void testReadAcrossMultipleBlocksWithExplicitBuffer() throws IOException {
        // Read a range that spans multiple blocks with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(30);
        int bytesRead = reader.readRange(10, 30, buffer);

        // Verify returned byte count
        assertEquals(30, bytesRead);

        // Verify buffer is ready for reading
        assertEquals(0, buffer.position());
        assertEquals(30, buffer.limit());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(10, 40), result);
    }

    @Test
    void testReadBeyondEndWithExplicitBuffer() throws IOException {
        // Try to read beyond the end of the file with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(10);
        int bytesRead = reader.readRange(textContent.length() - 5, 10, buffer);

        // Verify returned byte count (should be truncated)
        assertEquals(5, bytesRead);

        // Verify buffer is ready for reading
        assertEquals(0, buffer.position());
        assertEquals(5, buffer.limit());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(textContent.length() - 5), result);
    }

    @Test
    void testReadStartingBeyondEndWithExplicitBuffer() throws IOException {
        // Try to read from an offset beyond the end of the file with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(5);
        int bytesRead = reader.readRange(textContent.length() + 10, 5, buffer);

        // Should return 0 bytes read
        assertEquals(0, bytesRead);

        // Buffer should be flipped but with 0 remaining bytes
        assertEquals(0, buffer.position());
        assertEquals(0, buffer.limit());
    }

    @Test
    void testReadWithZeroLengthExplicitBuffer() throws IOException {
        // Try to read with a length of 0 with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(10);
        int bytesRead = reader.readRange(10, 0, buffer);

        // Should return 0 bytes read
        assertEquals(0, bytesRead);

        // Buffer should not be changed
        assertEquals(0, buffer.position());
        assertEquals(0, buffer.remaining());
    }

    @Test
    void testReadWithOffsetInBuffer() throws IOException {
        // Test reading with offset in the buffer
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.position(5); // Start at position 5

        int bytesRead = reader.readRange(16, 10, buffer);

        // Verify returned byte count
        assertEquals(10, bytesRead);

        // Verify buffer is ready for reading
        assertEquals(5, buffer.position()); // Position should be at original position
        assertEquals(15, buffer.limit()); // Limit should be original position + bytes read

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(textContent.substring(16, 26), result);
    }

    @Test
    void testReadWithNegativeOffsetExplicitBuffer() {
        // Try to read with a negative offset with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(10);
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(-1, 10, buffer));
    }

    @Test
    void testReadWithNegativeExplicitBuffer() {
        // Try to read with a negative length with explicit buffer
        ByteBuffer buffer = ByteBuffer.allocate(10);
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(0, -1, buffer));
    }

    @Test
    void testReadWithNullBuffer() {
        // Try to read with a null buffer
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(0, 10, null));
    }

    @Test
    void testReadWithInsufficientBufferCapacity() {
        // Try to read with a buffer that has insufficient capacity
        ByteBuffer buffer = ByteBuffer.allocate(5);
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(0, 10, buffer));
    }
}
