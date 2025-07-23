package io.tileverse.rangereader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleCloudStorageRangeReaderTest {

    private static final String BUCKET = "test-bucket";
    private static final String OBJECT_NAME = "test.pmtiles";
    private static final int CONTENT_LENGTH = 10000;
    private static final byte[] TEST_DATA = createTestData(CONTENT_LENGTH);

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    @Mock
    private ReadChannel readChannel;

    private GoogleCloudStorageRangeReader reader;
    private long currentSeekPosition = 0;

    /**
     * Creates test data with a predictable pattern.
     */
    private static byte[] createTestData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }
        return data;
    }

    @BeforeEach
    void setUp() throws IOException {
        BlobId blobId = BlobId.of(BUCKET, OBJECT_NAME);

        // Reset seek position
        currentSeekPosition = 0;

        // Make mocks lenient for this test class to avoid unnecessary stubbing errors
        lenient().when(storage.get(blobId)).thenReturn(blob);
        lenient().when(blob.exists()).thenReturn(true);
        lenient().when(blob.getSize()).thenReturn((long) CONTENT_LENGTH);

        // Setup mock for blob.reader()
        lenient().when(blob.reader()).thenReturn(readChannel);

        // Setup mock for seek operation
        lenient()
                .doAnswer(invocation -> {
                    currentSeekPosition = invocation.getArgument(0);
                    return null;
                })
                .when(readChannel)
                .seek(any(Long.class));

        // Setup mock for ReadChannel behavior
        lenient().when(readChannel.read(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            int remaining = buffer.remaining();
            int bytesToRead = (int) Math.min(remaining, TEST_DATA.length - currentSeekPosition);

            if (bytesToRead <= 0) {
                return -1; // EOF
            }

            // Copy data from the current seek position
            for (int i = 0; i < bytesToRead; i++) {
                buffer.put(TEST_DATA[(int) currentSeekPosition + i]);
            }

            currentSeekPosition += bytesToRead;
            return bytesToRead;
        });

        // Create the reader
        reader = new GoogleCloudStorageRangeReader(storage, BUCKET, OBJECT_NAME);
    }

    @Test
    void testGetSize() throws IOException {
        assertEquals(CONTENT_LENGTH, reader.size());
        verify(storage, times(1)).get(BlobId.of(BUCKET, OBJECT_NAME));
    }

    @Test
    void testReadEntireFile() throws IOException {
        ByteBuffer buffer = reader.readRange(0, CONTENT_LENGTH);

        assertEquals(CONTENT_LENGTH, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        assertArrayEquals(TEST_DATA, bytes);

        verify(blob).reader();
        verify(readChannel).seek(0L);
    }

    @Test
    void testReadRange() throws IOException {
        int offset = 100;
        int length = 500;

        ByteBuffer buffer = reader.readRange(offset, length);

        assertEquals(length, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        byte[] expectedBytes = Arrays.copyOfRange(TEST_DATA, offset, offset + length);
        assertArrayEquals(expectedBytes, bytes);

        verify(blob).reader();
        verify(readChannel).seek((long) offset);
    }

    @Test
    void testReadRangeBeyondEnd() throws IOException {
        int offset = CONTENT_LENGTH - 200;
        int length = 500; // Beyond the end
        int actualLength = 200; // Should be truncated to end of file

        ByteBuffer buffer = reader.readRange(offset, length);

        // Should only return up to the end of the file
        assertEquals(actualLength, buffer.remaining());

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        byte[] expectedBytes = Arrays.copyOfRange(TEST_DATA, offset, CONTENT_LENGTH);
        assertArrayEquals(expectedBytes, bytes);

        verify(blob).reader();
        verify(readChannel).seek((long) offset);
    }

    @Test
    void testReadZeroLength() throws IOException {
        ByteBuffer buffer = reader.readRange(100, 0);

        assertEquals(0, buffer.remaining());

        // Should not make any API calls for zero length
        verify(blob, never()).reader();
    }

    @Test
    void testReadWithNegativeOffset() {
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(-1, 10));
    }

    @Test
    void testReadWithNegativeLength() {
        assertThrows(IllegalArgumentException.class, () -> reader.readRange(0, -1));
    }

    @Test
    void testObjectNotExists() {
        // Override the default behavior for this specific test
        when(storage.get(BlobId.of(BUCKET, OBJECT_NAME))).thenReturn(null);

        assertThrows(IOException.class, () -> new GoogleCloudStorageRangeReader(storage, BUCKET, OBJECT_NAME));
    }

    @Test
    void testObjectExistsReturnsFalse() {
        // Override the default behavior for this specific test
        when(blob.exists()).thenReturn(false);

        assertThrows(IOException.class, () -> new GoogleCloudStorageRangeReader(storage, BUCKET, OBJECT_NAME));
    }

    @Test
    void testStorageExceptionDuringConstruction() {
        // Override the default behavior for this specific test
        when(storage.get(BlobId.of(BUCKET, OBJECT_NAME))).thenThrow(new StorageException(404, "Object not found"));

        assertThrows(IOException.class, () -> new GoogleCloudStorageRangeReader(storage, BUCKET, OBJECT_NAME));
    }

    @Test
    void testStorageExceptionDuringRead() throws IOException {
        // Override the default behavior for this specific test
        when(blob.reader()).thenThrow(new StorageException(500, "Storage error"));

        assertThrows(IOException.class, () -> reader.readRange(0, 100));
    }

    @Test
    void testUnexpectedContentLength() throws IOException {
        // This test is no longer applicable with the new streaming API
        // The ReadChannel approach handles partial reads naturally
        // So we can remove this test or adapt it for different error conditions

        // Test reading when channel returns -1 (EOF) immediately
        when(blob.reader()).thenReturn(readChannel);
        when(readChannel.read(any(ByteBuffer.class))).thenReturn(-1);

        ByteBuffer buffer = reader.readRange(0, 100);
        assertEquals(0, buffer.remaining(), "Should return empty buffer when EOF reached immediately");
    }

    @Test
    void testGetSizeFromCachedValue() throws IOException {
        // First call should query the blob
        assertEquals(CONTENT_LENGTH, reader.size());
        verify(storage, times(1)).get(BlobId.of(BUCKET, OBJECT_NAME));

        // Second call should use cached value
        assertEquals(CONTENT_LENGTH, reader.size());
        verify(storage, times(1)).get(BlobId.of(BUCKET, OBJECT_NAME)); // Still only one call
    }

    @Test
    void testStorageExceptionDuringSize() throws IOException {
        // Create a reader with content length = -1 to force size() to make a call
        when(storage.get(BlobId.of(BUCKET, OBJECT_NAME))).thenReturn(blob);
        when(blob.exists()).thenReturn(true);
        when(blob.getSize()).thenReturn(-1L); // Initial construction will get -1

        // Create reader successfully with -1 size
        GoogleCloudStorageRangeReader failingReader = new GoogleCloudStorageRangeReader(storage, BUCKET, OBJECT_NAME);

        // Now make size() fail when called
        when(storage.get(BlobId.of(BUCKET, OBJECT_NAME))).thenReturn(null);

        assertThrows(IOException.class, () -> failingReader.size());
    }

    @Test
    void testNullInputsInConstructor() {
        assertThrows(NullPointerException.class, () -> new GoogleCloudStorageRangeReader(null, BUCKET, OBJECT_NAME));
        assertThrows(NullPointerException.class, () -> new GoogleCloudStorageRangeReader(storage, null, OBJECT_NAME));
        assertThrows(NullPointerException.class, () -> new GoogleCloudStorageRangeReader(storage, BUCKET, null));
    }

    @Test
    void testClose() {
        // Should not throw any exception
        reader.close();
        // Verify no interactions with the storage client during close
    }

    @Test
    void testBuilderWithStorage() throws IOException {
        // Test builder with custom storage client
        GoogleCloudStorageRangeReader builtReader = GoogleCloudStorageRangeReader.builder()
                .storage(storage)
                .bucket(BUCKET)
                .objectName(OBJECT_NAME)
                .build();

        assertEquals(CONTENT_LENGTH, builtReader.size());
    }

    @Test
    void testBuilderWithProjectId() throws IOException {
        // Test builder with project ID (will use default storage)
        // This test would fail in a real environment without credentials,
        // but it tests the builder logic
        try {
            GoogleCloudStorageRangeReader.builder()
                    .projectId("test-project")
                    .bucket(BUCKET)
                    .objectName(OBJECT_NAME)
                    .build();
        } catch (Exception e) {
            // Expected in test environment - just verify we got past validation
            assertTrue(e.getMessage().contains("GCS object") || e.getMessage().contains("credentials"));
        }
    }

    @Test
    void testBuilderWithUri() throws IOException {
        URI uri = URI.create("gs://" + BUCKET + "/" + OBJECT_NAME);

        GoogleCloudStorageRangeReader builtReader = GoogleCloudStorageRangeReader.builder()
                .storage(storage)
                .uri(uri)
                .build();

        assertEquals(CONTENT_LENGTH, builtReader.size());
    }

    @Test
    void testBuilderValidation() {
        // Test missing bucket/object
        assertThrows(IllegalStateException.class, () -> GoogleCloudStorageRangeReader.builder()
                .build());

        // Test invalid URI scheme
        assertThrows(IllegalArgumentException.class, () -> GoogleCloudStorageRangeReader.builder()
                .uri(URI.create("http://example.com")));

        // Test URI without bucket
        assertThrows(IllegalArgumentException.class, () -> GoogleCloudStorageRangeReader.builder()
                .uri(URI.create("gs:///")));

        // Test URI without object
        assertThrows(IllegalArgumentException.class, () -> GoogleCloudStorageRangeReader.builder()
                .uri(URI.create("gs://bucket/")));
    }
}
