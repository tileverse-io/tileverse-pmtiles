package io.tileverse.core;

import io.tileverse.rangereader.AzureBlobRangeReader;
import io.tileverse.rangereader.BlockAlignedRangeReader;
import io.tileverse.rangereader.CachingRangeReader;
import io.tileverse.rangereader.FileRangeReader;
import io.tileverse.rangereader.HttpRangeReader;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.S3RangeReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Examples demonstrating how to use the RangeReaderBuilder with PMTilesReader.
 * <p>
 * This class shows how the builder pattern provides a clean, chainable API for
 * creating RangeReaders with various configurations, which can then be used with
 * PMTilesReader.
 */
public class RangeReaderBuilderExample {

    /**
     * Basic usage examples for common storage types.
     */
    public static void basicExamples()
            throws IOException, InvalidHeaderException, CompressionUtil.UnsupportedCompressionException {
        // Local file example
        try (RangeReader fileReader = FileRangeReader.builder()
                        .path(Path.of("/path/to/file.pmtiles"))
                        .build();
                PMTilesReader pmtiles = new PMTilesReader(fileReader)) {

            // Use the PMTilesReader
            System.out.println("Zoom levels: " + pmtiles.getHeader().minZoom() + " to "
                    + pmtiles.getHeader().maxZoom());
        }

        // HTTP example with caching
        try (RangeReader httpReader = CachingRangeReader.builder()
                        .delegate(HttpRangeReader.builder()
                                .uri(URI.create("https://example.com/tiles.pmtiles"))
                                .build())
                        .build();
                PMTilesReader pmtiles = new PMTilesReader(httpReader)) {

            // Use the PMTilesReader
            Optional<byte[]> tile = pmtiles.getTile(0, 0, 0);
        }

        // S3 example with caching and block alignment
        try (RangeReader s3Reader = CachingRangeReader.builder()
                        .delegate(BlockAlignedRangeReader.builder()
                                .delegate(S3RangeReader.builder()
                                        .uri(URI.create("s3://bucket/tiles.pmtiles"))
                                        .build())
                                .blockSize(64 * 1024) // 64KB blocks
                                .build())
                        .build();
                PMTilesReader pmtiles = new PMTilesReader(s3Reader)) {

            // Use the PMTilesReader
            System.out.println("Tile type: " + pmtiles.getHeader().tileType());
        }

        // Azure example with connection string
        try (RangeReader azureReader = CachingRangeReader.builder()
                        .delegate(AzureBlobRangeReader.builder()
                                .connectionString(
                                        "DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=key==")
                                .containerName("container")
                                .blobPath("path/to/tiles.pmtiles")
                                .build())
                        .build();
                PMTilesReader pmtiles = new PMTilesReader(azureReader)) {

            // Use the PMTilesReader
            System.out.println("Min zoom: " + pmtiles.getHeader().minZoom());
        }
    }

    /**
     * Advanced configuration examples.
     */
    public static void advancedExamples()
            throws IOException, InvalidHeaderException, CompressionUtil.UnsupportedCompressionException {
        // S3 with custom authentication and region
        try (RangeReader s3Reader = CachingRangeReader.builder()
                        .delegate(BlockAlignedRangeReader.builder()
                                .delegate(S3RangeReader.builder()
                                        .uri(URI.create("s3://bucket/tiles.pmtiles"))
                                        .credentialsProvider(ProfileCredentialsProvider.builder()
                                                .profileName("dev")
                                                .build())
                                        .region(Region.US_WEST_2)
                                        .build())
                                .blockSize(16384) // Custom block size (16KB)
                                .build())
                        .build();
                PMTilesReader pmtiles = new PMTilesReader(s3Reader)) {

            // Use the PMTilesReader
            System.out.println("Max zoom: " + pmtiles.getHeader().maxZoom());
        }

        // HTTP with self-signed certificates
        try (RangeReader httpReader = CachingRangeReader.builder()
                        .delegate(HttpRangeReader.builder()
                                .uri(URI.create("https://internal-server.example.com/tiles.pmtiles"))
                                .trustAllCertificates()
                                .build())
                        .build();
                PMTilesReader pmtiles = new PMTilesReader(httpReader)) {

            // Use the PMTilesReader
            System.out.println("Bounds: " + pmtiles.getHeader().minLonE7() / 10000000.0 + ","
                    + pmtiles.getHeader().minLatE7() / 10000000.0);
        }

        // Azure with account credentials
        try (RangeReader azureReader = CachingRangeReader.builder()
                        .delegate(BlockAlignedRangeReader.builder()
                                .delegate(AzureBlobRangeReader.builder()
                                        .accountCredentials("myaccount", "accountkey==")
                                        .containerName("container")
                                        .blobPath("tiles.pmtiles")
                                        .build())
                                .blockSize(32768) // 32KB blocks
                                .build())
                        .build();
                PMTilesReader pmtiles = new PMTilesReader(azureReader)) {

            // Use the PMTilesReader
            System.out.println("Center: " + pmtiles.getHeader().centerZoom() + "/"
                    + pmtiles.getHeader().centerLonE7() / 10000000.0
                    + "," + pmtiles.getHeader().centerLatE7() / 10000000.0);
        }
    }

    /**
     * Examples where we customize the reader in a reusable way.
     */
    public static void customizationExamples()
            throws IOException, InvalidHeaderException, CompressionUtil.UnsupportedCompressionException {
        // Helper method to create optimized readers with consistent settings
        int blockSize = 16384;

        // S3 example with consistent optimization pattern
        try (RangeReader s3Reader = CachingRangeReader.builder()
                        .delegate(BlockAlignedRangeReader.builder()
                                .delegate(S3RangeReader.builder()
                                        .uri(URI.create("s3://bucket1/tiles.pmtiles"))
                                        .build())
                                .blockSize(blockSize)
                                .build())
                        .build();
                PMTilesReader pmtiles1 = new PMTilesReader(s3Reader)) {

            // Use the first PMTilesReader
            System.out.println("First tileset: " + pmtiles1.getHeader().maxZoom());
        }

        // Azure example with the same optimization pattern
        try (RangeReader azureReader = CachingRangeReader.builder()
                        .delegate(BlockAlignedRangeReader.builder()
                                .delegate(AzureBlobRangeReader.builder()
                                        .uri(URI.create(
                                                "azure://account.blob.core.windows.net/container/tiles.pmtiles"))
                                        .build())
                                .blockSize(blockSize)
                                .build())
                        .build();
                PMTilesReader pmtiles2 = new PMTilesReader(azureReader)) {

            // Use the second PMTilesReader
            System.out.println("Second tileset: " + pmtiles2.getHeader().maxZoom());
        }
    }

    /**
     * Main method.
     */
    public static void main(String[] args) {
        try {
            System.out.println("These examples are for demonstration purposes only.");
            System.out.println("To run them, you would need to provide actual paths and credentials.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
