package io.tileverse.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.tileverse.rangereader.AzureBlobRangeReader;
import io.tileverse.rangereader.BlockAlignedRangeReader;
import io.tileverse.rangereader.CachingRangeReader;
import io.tileverse.rangereader.HttpRangeReader;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.S3RangeReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Integration tests for cloud storage access with PMTilesReader using RangeReaderBuilder.
 * <p>
 * These tests demonstrate accessing PMTiles files from different cloud storage providers
 * using the RangeReaderBuilder and PMTilesReader.
 * <p>
 * NOTE: These tests require actual cloud storage access, so they are marked with the
 * "integration" tag and are skipped by default. To run them, you need to:
 * 1. Have the necessary cloud storage credentials configured
 * 2. Have test PMTiles files uploaded to the appropriate locations
 * 3. Configure the test properties via environment variables
 * 4. Run Maven with the integration profile: mvn test -Pintegration
 */
@Tag("integration")
public class CloudStorageIntegrationTest {

    // Test configuration from environment variables
    private static String s3Bucket;
    private static String s3Key;
    private static String s3Region;

    private static String azureConnectionString;
    private static String azureContainer;
    private static String azureBlob;

    private static String httpUrl;

    @BeforeAll
    public static void setup() {
        // S3 configuration
        s3Bucket = System.getenv("TEST_S3_BUCKET");
        s3Key = System.getenv("TEST_S3_KEY");
        s3Region = System.getenv("TEST_S3_REGION");

        // Azure configuration
        azureConnectionString = System.getenv("TEST_AZURE_CONNECTION_STRING");
        azureContainer = System.getenv("TEST_AZURE_CONTAINER");
        azureBlob = System.getenv("TEST_AZURE_BLOB");

        // HTTP configuration
        httpUrl = System.getenv("TEST_HTTP_URL");
    }

    /**
     * Test reading a PMTiles file from S3 using the RangeReaderBuilder.
     * <p>
     * This test demonstrates:
     * - Creating an S3 RangeReader with the builder pattern
     * - Configuring region and credentials
     * - Adding performance optimizations (caching and block alignment)
     * - Reading the PMTiles header and a tile
     */
    @Test
    void testReadPMTilesFromS3()
            throws IOException, InvalidHeaderException, CompressionUtil.UnsupportedCompressionException {
        assumeTrue(
                s3Bucket != null && s3Key != null && s3Region != null,
                "S3 test configuration not found in environment variables");

        URI s3Uri = URI.create("s3://" + s3Bucket + "/" + s3Key);

        try (RangeReader reader = CachingRangeReader.builder()
                        .delegate(BlockAlignedRangeReader.builder()
                                .delegate(S3RangeReader.builder()
                                        .uri(s3Uri)
                                        .region(Region.of(s3Region))
                                        .credentialsProvider(DefaultCredentialsProvider.create())
                                        .build())
                                .blockSize(16384)
                                .build())
                        .build();
                PMTilesReader pmTiles = new PMTilesReader(reader)) {

            // Verify we can read the header
            PMTilesHeader header = pmTiles.getHeader();
            assertNotNull(header);
            System.out.println("PMTiles header: version=" + header.version() + ", minZoom=" + header.minZoom()
                    + ", maxZoom=" + header.maxZoom());

            // Try to read a tile
            int z = header.minZoom();
            Optional<byte[]> tileData = pmTiles.getTile(z, 0, 0);
            assertTrue(tileData.isPresent(), "Should be able to read a tile at minimum zoom level");
            System.out.println("Successfully read tile at z=" + z + " with size " + tileData.get().length + " bytes");
        }
    }

    /**
     * Test reading a PMTiles file from Azure Blob Storage using the RangeReaderBuilder.
     * <p>
     * This test demonstrates:
     * - Creating an Azure Blob RangeReader with the builder pattern
     * - Configuring connection string and container/blob path
     * - Adding performance optimizations (caching and block alignment)
     * - Reading the PMTiles header and metadata
     */
    @Test
    void testReadPMTilesFromAzure()
            throws IOException, InvalidHeaderException, CompressionUtil.UnsupportedCompressionException {
        assumeTrue(
                azureConnectionString != null && azureContainer != null && azureBlob != null,
                "Azure test configuration not found in environment variables");

        try (RangeReader reader = CachingRangeReader.builder()
                        .delegate(BlockAlignedRangeReader.builder()
                                .delegate(AzureBlobRangeReader.builder()
                                        .connectionString(azureConnectionString)
                                        .containerName(azureContainer)
                                        .blobPath(azureBlob)
                                        .build())
                                .blockSize(32768)
                                .build())
                        .build();
                PMTilesReader pmTiles = new PMTilesReader(reader)) {

            // Verify we can read the header
            PMTilesHeader header = pmTiles.getHeader();
            assertNotNull(header);
            System.out.println("PMTiles header: version=" + header.version() + ", minZoom=" + header.minZoom()
                    + ", maxZoom=" + header.maxZoom());

            // Try to read the metadata
            byte[] metadata = pmTiles.getMetadata();
            assertNotNull(metadata);
            assertTrue(metadata.length > 0, "Metadata should not be empty");
            System.out.println("Successfully read metadata with size " + metadata.length + " bytes");
            System.out.println("Metadata preview: "
                    + new String(metadata, 0, Math.min(100, metadata.length), StandardCharsets.UTF_8) + "...");
        }
    }

    /**
     * Test reading a PMTiles file from an HTTP URL using the RangeReaderBuilder.
     * <p>
     * This test demonstrates:
     * - Creating an HTTP RangeReader with the builder pattern
     * - Configuring to trust all certificates (for self-signed certs)
     * - Adding caching for performance
     * - Reading the PMTiles header and a specific tile
     */
    @Test
    void testReadPMTilesFromHttp()
            throws IOException, InvalidHeaderException, CompressionUtil.UnsupportedCompressionException {
        assumeTrue(httpUrl != null, "HTTP test configuration not found in environment variables");

        URI httpUri = URI.create(httpUrl);

        try (RangeReader reader = CachingRangeReader.builder()
                        .delegate(HttpRangeReader.builder()
                                .uri(httpUri)
                                .trustAllCertificates()
                                .build())
                        .build();
                PMTilesReader pmTiles = new PMTilesReader(reader)) {

            // Verify we can read the header
            PMTilesHeader header = pmTiles.getHeader();
            assertNotNull(header);
            System.out.println("PMTiles header: version=" + header.version() + ", minZoom=" + header.minZoom()
                    + ", maxZoom=" + header.maxZoom());

            // Try to read a tile from a specific zoom level
            int z = header.minZoom() + 1;
            if (z <= header.maxZoom()) {
                Optional<byte[]> tileData = pmTiles.getTile(z, 0, 0);
                assertTrue(tileData.isPresent(), "Should be able to read a tile at zoom level " + z);
                System.out.println(
                        "Successfully read tile at z=" + z + " with size " + tileData.get().length + " bytes");
            }
        }
    }
}
