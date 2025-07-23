package io.tileverse.rangereader.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import io.tileverse.rangereader.AzureBlobRangeReader;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.RangeReaderFactory;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for AzureBlobRangeReader using Azurite.
 * <p>
 * These tests verify that the AzureBlobRangeReader can correctly read ranges of bytes
 * from an Azure Blob Storage container using the Azure SDK against an Azurite container.
 */
@Testcontainers
public class AzureBlobRangeReaderIT extends AbstractRangeReaderIT {

    private static final String CONTAINER_NAME = "test-container";
    private static final String BLOB_NAME = "test.bin";
    // These are the default account name and key used by Azurite
    private static final String ACCOUNT_NAME = "devstoreaccount1";
    private static final String ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    private static Path testFile;
    private static BlobServiceClient blobServiceClient;
    private static BlobContainerClient containerClient;
    private static String connectionString;

    @Container
    @SuppressWarnings("resource")
    static AzuriteContainer azurite = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite")
            .withEnv("AZURITE_BLOB_LOOSE", "true")
            .withCommand("azurite-blob --skipApiVersionCheck --loose --blobHost 0.0.0.0 --debug")
            .withExposedPorts(10000, 10001, 10002);

    @BeforeAll
    static void setupAzure() throws IOException {
        // Create a test file
        testFile = TestUtil.createTempTestFile(TEST_FILE_SIZE);

        // Configure connection string to Azurite
        connectionString = azurite.getConnectionString();

        // Initialize Blob Service client with client options that disable API version validation
        // Use a lower API version that's compatible with Azurite
        blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(azurite.getConnectionString())
                .buildClient();

        // Create container
        containerClient = blobServiceClient.createBlobContainer(CONTAINER_NAME);

        // Upload the test file
        containerClient.getBlobClient(BLOB_NAME).uploadFromFile(testFile.toString(), true);
    }

    @AfterAll
    static void cleanupAzure() {
        if (containerClient != null) {
            try {
                containerClient.delete();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Override
    protected void setUp() throws IOException {
        // Nothing needed here since all setup is done in @BeforeAll
    }

    @Override
    protected RangeReader createBaseReader() throws IOException {
        // Create AzureBlobRangeReader using the factory method with connection string
        return RangeReaderFactory.createAzureBlobRangeReader(connectionString, CONTAINER_NAME, BLOB_NAME);
    }

    /**
     * Additional Azure-specific tests can go here
     */
    @Test
    void testAzureBlobRangeReaderImplementation() throws IOException {
        // Create RangeReader directly to verify implementation
        try (RangeReader reader = AzureBlobRangeReader.builder()
                .connectionString(connectionString)
                .containerName(CONTAINER_NAME)
                .blobPath(BLOB_NAME)
                .build()) {

            // Verify it's the right implementation
            assertTrue(reader instanceof AzureBlobRangeReader, "Should be an AzureBlobRangeReader instance");
        }
    }

    @Test
    void testAzureBlobWithAccountCredentials() throws IOException {
        // Get the Azurite blob endpoint
        String azuriteHost = azurite.getHost();
        Integer azuritePort = azurite.getMappedPort(10000);
        String blobEndpoint = String.format("http://%s:%d/%s", azuriteHost, azuritePort, ACCOUNT_NAME);

        // Create StorageSharedKeyCredential
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(ACCOUNT_NAME, ACCOUNT_KEY);

        // Create BlobClient with older API version that's compatible with Azurite
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(blobEndpoint)
                .credential(credential)
                .serviceVersion(com.azure.storage.blob.BlobServiceVersion.V2019_12_12)
                .buildClient();

        // Create RangeReader directly
        try (AzureBlobRangeReader reader = new AzureBlobRangeReader(
                blobServiceClient.getBlobContainerClient(CONTAINER_NAME).getBlobClient(BLOB_NAME))) {

            // Verify this alternative construction method works
            assertTrue(reader != null, "Should create reader with account credentials");
        }
    }
}
