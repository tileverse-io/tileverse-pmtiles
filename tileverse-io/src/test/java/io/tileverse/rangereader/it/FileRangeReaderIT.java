package io.tileverse.rangereader.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.tileverse.rangereader.FileRangeReader;
import io.tileverse.rangereader.RangeReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for FileRangeReader.
 * <p>
 * These tests verify that the FileRangeReader can correctly read ranges of bytes
 * from a file on the local filesystem.
 */
public class FileRangeReaderIT extends AbstractRangeReaderIT {

    private static Path testFilePath;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setupTestFile() throws IOException {
        // Create a test file with known content
        testFilePath = TestUtil.createTempTestFile(TEST_FILE_SIZE);
    }

    @Override
    protected void setUp() throws IOException {
        // Make the test file accessible to the abstract test class
        testFile = testFilePath;
    }

    @AfterEach
    void cleanupTestPaths() {
        // No specific cleanup needed for FileRangeReader tests
    }

    @Override
    protected RangeReader createBaseReader() throws IOException {
        return new FileRangeReader(testFilePath);
    }

    /**
     * Additional File-specific tests can go here
     */
    @Test
    void testFileRangeReaderBuilder() throws IOException {
        // Create a temporary file for this specific test
        Path tempFile = Files.createTempFile(tempDir, "filetest", ".bin");
        TestUtil.createMockTestFile(tempFile, 1024);

        // Create RangeReader using builder
        try (RangeReader reader = FileRangeReader.builder().path(tempFile).build()) {

            // Verify it's the right implementation
            assertTrue(reader instanceof FileRangeReader, "Should be a FileRangeReader instance");

            // Verify size matches
            assertEquals(1024, reader.size(), "File size should match");

            // Read some data to verify it works correctly
            ByteBuffer buffer = reader.readRange(0, 10);
            assertEquals(10, buffer.remaining(), "Should read 10 bytes");
        }
    }

    @SuppressWarnings("resource")
    @Test
    void testFileRangeReaderWithNonExistentFile() {
        // Test creating a reader with a file that doesn't exist
        Path nonExistentFile = tempDir.resolve("non-existent-file.bin");

        try {
            new FileRangeReader(nonExistentFile);
            // The line above should throw an exception, so if we get here, the test failed
            assertTrue(false, "Creating a reader with a non-existent file should throw an exception");
        } catch (IOException e) {
            // Expected exception
            assertTrue(
                    e.getMessage().contains("NoSuchFileException")
                            || e.getMessage().contains("non-existent-file.bin"),
                    "Exception should mention the missing file");
        }
    }

    @Test
    void testConcurrentReads() throws Exception {
        // Test concurrent reads from the same file
        try (RangeReader reader = createBaseReader()) {
            // Create multiple threads to read different parts of the file simultaneously
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            ByteBuffer[] buffers = new ByteBuffer[threadCount];
            boolean[] success = new boolean[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                final int offset = i * 1000; // Different offset for each thread
                buffers[i] = ByteBuffer.allocate(100);

                threads[i] = new Thread(() -> {
                    try {
                        reader.readRange(offset, 100, buffers[threadIndex]);
                        success[threadIndex] = true;
                    } catch (Exception e) {
                        success[threadIndex] = false;
                    }
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Verify all reads were successful
            for (int i = 0; i < threadCount; i++) {
                assertTrue(success[i], "Thread " + i + " should have completed successfully");
                assertEquals(100, buffers[i].remaining(), "Thread " + i + " should have read 100 bytes");
            }

            // Verify that each thread got different data (based on different offsets)
            for (int i = 0; i < threadCount - 1; i++) {
                ByteBuffer buf1 = buffers[i].duplicate();
                ByteBuffer buf2 = buffers[i + 1].duplicate();

                // Get first bytes to compare
                byte byte1 = buf1.get(0);
                byte byte2 = buf2.get(0);

                // Threads read from different offsets, so the data should be different
                // Note: There's a small chance this could fail if the random data happens to
                // have the same byte at different offsets, but the probability is very low
                assertTrue(
                        byte1 != byte2 || buf1.get(1) != buf2.get(1),
                        "Data from different offsets should be different");
            }
        }
    }
}
