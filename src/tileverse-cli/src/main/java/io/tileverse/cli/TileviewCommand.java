/*
 * (c) Copyright 2025 Multiversio LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tileverse.cli;

import io.tileverse.pmtiles.CompressionUtil;
import io.tileverse.pmtiles.InvalidHeaderException;
import io.tileverse.pmtiles.PMTilesHeader;
import io.tileverse.pmtiles.PMTilesReader;
import io.tileverse.pmtiles.ZXY;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.RangeReaderBuilder;
import io.tileverse.rangereader.azure.AzureBlobRangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.rangereader.file.FileRangeReader;
import io.tileverse.rangereader.http.HttpRangeReader;
import io.tileverse.rangereader.s3.S3RangeReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command for viewing PMTiles information.
 * This emulates the functionality of tileview, tile-inspect, and similar tools.
 */
@Command(name = "tileview", description = "View information about PMTiles files (local or remote)")
public class TileviewCommand implements Callable<Integer> {

    @Parameters(
            paramLabel = "FILE",
            description = "Input PMTiles file or URI (e.g., file, http, s3, azure)",
            arity = "1")
    private String input;

    @Option(
            names = {"--cache"},
            description = "Enable memory caching for improved performance with remote files")
    private boolean enableCaching;

    @Option(
            names = {"--trust-all-certs"},
            description = "Trust all certificates when using HTTPS")
    private boolean trustAllCertificates;

    @Option(
            names = {"--aws-region"},
            description = "AWS region for S3 access")
    private String awsRegion;

    @Option(
            names = {"--azure-sas-token"},
            description = "Azure SAS token for blob access")
    private String azureSasToken;

    @Option(
            names = {"-z", "--zoom"},
            paramLabel = "ZOOM",
            description = "Zoom level to view")
    private Integer zoom;

    @Option(
            names = {"-x", "--x"},
            paramLabel = "X",
            description = "X coordinate to view")
    private Integer x;

    @Option(
            names = {"-y", "--y"},
            paramLabel = "Y",
            description = "Y coordinate to view")
    private Integer y;

    @Option(names = "--metadata", description = "Display metadata only")
    private boolean metadataOnly;

    @Option(names = "--summary", description = "Display summary information")
    private boolean summary;

    @Option(names = "--json", description = "Output in JSON format")
    private boolean json;

    @Option(names = "--extract", description = "Extract tile to file (requires --zoom, --x, --y arguments)")
    private boolean extract;

    @Option(names = "--try-flipped-y", description = "Try both regular and TMS-flipped Y coordinates")
    private boolean tryFlippedY;

    @Option(names = "--debug-search", description = "Use exhaustive search to find closest tile (slow)")
    private boolean debugSearch;

    @Option(
            names = {"-o", "--output"},
            paramLabel = "FILE",
            description = "Output file for extracted tile (default: tile_z_x_y.mvt)")
    private File outputFile;

    @Override
    public Integer call() throws Exception {
        // Create a RangeReader for the input - could be a file path or URI
        RangeReader rangeReader = createRangeReader(input);

        if (rangeReader == null) {
            return 1; // Error already reported
        }

        // Open the PMTiles file
        try (PMTilesReader reader = new PMTilesReader(rangeReader)) {
            PMTilesHeader header = reader.getHeader();

            // Extract a specific tile
            if (extract) {
                return extractTile(reader);
            }

            // Check which information to display
            if (metadataOnly) {
                return displayMetadata(reader);
            } else if (zoom != null && x != null && y != null) {
                return displayTile(reader, zoom, x, y);
            } else if (summary) {
                return displaySummary(reader);
            } else {
                // Default to showing the summary
                return displaySummary(reader);
            }
        } catch (InvalidHeaderException e) {
            System.err.println("Error: Not a valid PMTiles file: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Creates a RangeReader for the given input.
     * The input can be a file path or a URI (http, s3, azure).
     *
     * @param input the input string, either a file path or URI
     * @return a RangeReader for the input, or null if the input is invalid
     */
    private RangeReader createRangeReader(String input) {
        try {
            // Check if the input is a URI
            if (input.startsWith("http://")
                    || input.startsWith("https://")
                    || input.startsWith("s3://")
                    || input.startsWith("azure://")) {

                // Parse the URI
                URI uri = URI.create(input);
                return createReaderFromUri(uri);
            }

            // Try to detect if this is a URL without a scheme
            if (looksLikeUrl(input)) {
                // Assume HTTP and prepend the scheme
                String urlWithScheme = "https://" + input;
                System.out.println("Note: Treating input as HTTPS URL: " + urlWithScheme);
                URI uri = URI.create(urlWithScheme);
                return createReaderFromUri(uri);
            }

            // Check if the input is a local file
            File inputFile = new File(input);
            if (!inputFile.exists()) {
                System.err.println("Error: Input file does not exist: " + inputFile);
                return null;
            }

            if (!inputFile.isFile()) {
                System.err.println("Error: Input is not a file: " + inputFile);
                return null;
            }

            Path inputPath = inputFile.toPath();

            // Create a FileRangeReader with optional caching
            RangeReader baseReader = FileRangeReader.builder().path(inputPath).build();
            return applyCommonOptions(baseReader);

        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid URI format: " + e.getMessage());
            return null;
        } catch (IOException e) {
            System.err.println("Error: Failed to create reader: " + e.getMessage());
            return null;
        }
    }

    /**
     * Tries to determine if a string looks like a URL without a scheme.
     * This is a heuristic and may not catch all cases.
     */
    private boolean looksLikeUrl(String input) {
        // Check for common URL patterns
        return input.contains(".")
                && !input.contains(" ")
                && !input.contains("\\")
                && (input.contains("/") || input.matches("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:[0-9]+)?$"));
    }

    /**
     * Creates a RangeReader from a URI.
     *
     * @param uri the URI to create a reader for
     * @return a RangeReader for the URI
     * @throws IOException if an I/O error occurs
     */
    private RangeReader createReaderFromUri(URI uri) throws IOException {
        // Handle different URI schemes and create the appropriate base reader
        String scheme = uri.getScheme().toLowerCase();
        RangeReader baseReader =
                switch (scheme) {
                    case "http", "https" -> {
                        var httpBuilder = HttpRangeReader.builder().uri(uri);
                        if (trustAllCertificates) {
                            httpBuilder.trustAllCertificates();
                        }
                        yield httpBuilder.build();
                    }
                    case "s3" -> {
                        var s3Builder = S3RangeReader.builder().uri(uri);
                        // Set region if provided
                        if (awsRegion != null && !awsRegion.isEmpty()) {
                            s3Builder.region(software.amazon.awssdk.regions.Region.of(awsRegion));
                        }
                        // Use default credentials chain
                        yield s3Builder.build();
                    }
                    case "azure" -> {
                        // Parse account, container, and blob from URI
                        // Expected format: azure://account/container/blob
                        String path = uri.getPath();
                        if (path == null || path.isEmpty() || path.equals("/")) {
                            throw new IOException("Invalid Azure URI format. Expected: azure://account/container/blob");
                        }

                        String account = uri.getHost();
                        String[] pathParts = path.substring(1).split("/", 2);

                        if (pathParts.length < 2) {
                            throw new IOException("Invalid Azure URI format. Expected: azure://account/container/blob");
                        }

                        String container = pathParts[0];
                        String blob = pathParts[1];

                        var azureBuilder = AzureBlobRangeReader.builder()
                                .accountName(account)
                                .containerName(container)
                                .blobPath(blob);

                        // Use SAS token if provided
                        if (azureSasToken != null && !azureSasToken.isEmpty()) {
                            azureBuilder.sasToken(azureSasToken);
                        }
                        // Otherwise use default credentials
                        yield azureBuilder.build();
                    }
                    default -> throw new IOException("Unsupported URI scheme: " + scheme);
                };

        // Apply common options (caching, etc.)
        return applyCommonOptions(baseReader);
    }

    /**
     * Applies common options to a base RangeReader.
     *
     * @param baseReader the base reader to apply decorations to
     * @return the decorated reader
     */
    private RangeReader applyCommonOptions(RangeReader baseReader) throws IOException {
        if (enableCaching) {
            // Apply memory caching for better performance
            return CachingRangeReader.builder(baseReader).build();
        }
        return baseReader;
    }

    /**
     * Helper method to get the scheme from a URI builder if available.
     *
     * @param builder the RangeReaderBuilder
     * @return the scheme or null if not available
     */
    private String getBuilderScheme(RangeReaderBuilder builder) {
        // This is just a placeholder - we can't actually access this information
        // directly from the builder in a type-safe way
        return null;
    }

    /**
     * Displays metadata from the PMTiles file.
     */
    private int displayMetadata(PMTilesReader reader)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        PMTilesHeader header = reader.getHeader();

        // Get and parse the metadata
        byte[] metadataBytes = reader.getMetadata();
        String metadata = new String(metadataBytes, StandardCharsets.UTF_8);

        // Display header information
        System.out.println("PMTiles Header:");
        System.out.println("====================");
        System.out.println("Min zoom: " + header.minZoom());
        System.out.println("Max zoom: " + header.maxZoom());
        System.out.println("Tile count: " + header.addressedTilesCount());
        System.out.println("Unique tile contents: " + header.tileContentsCount());
        System.out.println("Compression: " + compressionTypeToString(header.tileCompression()));
        System.out.println("Tile type: " + tileTypeToString(header.tileType()));
        System.out.println("Bounds: " + header.minLonE7() / 10000000.0
                + "," + header.minLatE7() / 10000000.0
                + "," + header.maxLonE7() / 10000000.0
                + "," + header.maxLatE7() / 10000000.0);
        System.out.println("Center: " + header.centerLonE7() / 10000000.0
                + "," + header.centerLatE7() / 10000000.0
                + " (zoom " + header.centerZoom() + ")");

        // Display metadata JSON
        System.out.println("\nMetadata JSON:");
        System.out.println(formatJson(metadata));

        return 0;
    }

    /**
     * Displays information about a specific tile.
     */
    private int displayTile(PMTilesReader reader, int zoom, int x, int y)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        PMTilesHeader header = reader.getHeader();

        // Validate the zoom level
        if (zoom < header.minZoom() || zoom > header.maxZoom()) {
            System.err.println("Error: Zoom level " + zoom + " is outside the valid range " + header.minZoom() + " to "
                    + header.maxZoom());
            return 1;
        }

        // Validate the coordinates
        int maxCoord = (1 << zoom) - 1;
        if (x < 0 || x > maxCoord || y < 0 || y > maxCoord) {
            System.err.println(
                    "Error: Coordinates (" + x + "," + y + ") are outside the valid range (0-" + maxCoord + ")");
            return 1;
        }

        // Get the tile data
        Optional<byte[]> tileData = reader.getTile(zoom, x, y);

        if (tileData.isEmpty()) {
            System.out.println("Tile " + zoom + "/" + x + "/" + y + " not found in the PMTiles file.");
            return 1;
        }

        // Display tile information
        System.out.println("Tile " + zoom + "/" + x + "/" + y + ":");
        System.out.println("====================");
        System.out.println("Size: " + tileData.get().length + " bytes");

        // For MVT tiles, we could parse and display more information
        if (header.tileType() == PMTilesHeader.TILETYPE_MVT) {
            System.out.println("Format: MVT (Mapbox Vector Tile)");
            // In a full implementation, we'd parse the MVT format here
            // and show information about layers, features, etc.
        } else if (header.tileType() == PMTilesHeader.TILETYPE_PNG) {
            System.out.println("Format: PNG");
        } else if (header.tileType() == PMTilesHeader.TILETYPE_JPEG) {
            System.out.println("Format: JPEG");
        } else if (header.tileType() == PMTilesHeader.TILETYPE_WEBP) {
            System.out.println("Format: WebP");
        } else {
            System.out.println("Format: Unknown (" + header.tileType() + ")");
        }

        return 0;
    }

    /**
     * Displays summary information about the PMTiles file.
     */
    private int displaySummary(PMTilesReader reader)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        PMTilesHeader header = reader.getHeader();

        // Get metadata
        byte[] metadataBytes = reader.getMetadata();
        String metadata = new String(metadataBytes, StandardCharsets.UTF_8);

        // Display summary information
        System.out.println("PMTiles Summary:");
        System.out.println("====================");
        System.out.println("Source: " + input);

        // For local files, show the file size
        if (!input.startsWith("http://")
                && !input.startsWith("https://")
                && !input.startsWith("s3://")
                && !input.startsWith("azure://")) {
            File inputFile = new File(input);
            System.out.println("Size: " + formatFileSize(inputFile.length()));
        }
        System.out.println("Zoom levels: " + header.minZoom() + " to " + header.maxZoom());
        System.out.println("Tile count: " + header.addressedTilesCount());
        System.out.println("Unique tile contents: " + header.tileContentsCount());
        System.out.println("Compression: " + compressionTypeToString(header.tileCompression()));
        System.out.println("Tile type: " + tileTypeToString(header.tileType()));
        System.out.println("Bounds: " + header.minLonE7() / 10000000.0
                + "," + header.minLatE7() / 10000000.0
                + "," + header.maxLonE7() / 10000000.0
                + "," + header.maxLatE7() / 10000000.0);

        // Count tiles per zoom level (if not too many tiles)
        if (header.addressedTilesCount() < 1000000) {
            System.out.println("\nTiles per zoom level:");
            countTilesPerZoom(reader);
        }

        return 0;
    }

    /**
     * Extracts a tile to a file.
     */
    private int extractTile(PMTilesReader reader) throws IOException, CompressionUtil.UnsupportedCompressionException {
        // Validate the tile coordinates
        if (zoom == null || x == null || y == null) {
            System.err.println("Error: Extracting a tile requires --zoom, --x, and --y arguments");
            return 1;
        }

        PMTilesHeader header = reader.getHeader();

        // Validate the zoom level
        if (zoom < header.minZoom() || zoom > header.maxZoom()) {
            System.err.println("Error: Zoom level " + zoom + " is outside the valid range " + header.minZoom() + " to "
                    + header.maxZoom());
            return 1;
        }

        // Validate the coordinates
        int maxCoord = (1 << zoom) - 1;
        if (x < 0 || x > maxCoord || y < 0 || y > maxCoord) {
            System.err.println(
                    "Error: Coordinates (" + x + "," + y + ") are outside the valid range (0-" + maxCoord + ")");
            return 1;
        }

        // Get the tile data
        System.out.println("Requesting tile " + zoom + "/" + x + "/" + y + "...");

        // Print header info for debugging
        System.out.println("File contains zoom levels: " + header.minZoom() + " to " + header.maxZoom());
        System.out.println("Total tiles: " + header.addressedTilesCount());

        // Warn if coordinates are outside bounds
        int numTilesAtZoom = 1 << zoom;
        System.out.println("Valid X/Y range at zoom " + zoom + ": 0 to " + (numTilesAtZoom - 1));

        Optional<byte[]> tileData = Optional.empty();
        try {
            // Try with regular Y coordinate
            tileData = reader.getTile(zoom, x, y);

            // If not found and tryFlippedY is enabled, try with flipped Y coordinate
            if (tileData.isEmpty() && tryFlippedY) {
                int flippedY = (numTilesAtZoom - 1) - y;
                System.out.println(
                        "\nTile not found with standard Y coordinate. Trying with TMS flipped Y coordinate...");
                System.out.println(
                        "Converting Y coordinate " + y + " to flipped Y coordinate " + flippedY + " (TMS style)");

                tileData = reader.getTile(zoom, x, flippedY);

                if (tileData.isPresent()) {
                    System.out.println(
                            "Success! Tile found with flipped Y coordinate: " + zoom + "/" + x + "/" + flippedY);
                    // Update y to the flipped value for the rest of the processing
                    y = flippedY;
                }
            }

            if (tileData.isEmpty()) {
                // Try getting a different tile at the same zoom level to see if any tiles exist
                int testX = numTilesAtZoom / 2;
                int testY = numTilesAtZoom / 2;
                System.out.println("\nTrying to get a test tile at " + zoom + "/" + testX + "/" + testY
                        + " to verify zoom level access...");
                Optional<byte[]> testTile = reader.getTile(zoom, testX, testY);

                if (testTile.isPresent()) {
                    System.out.println("Successfully found test tile at " + zoom + "/" + testX + "/" + testY);
                    System.out.println("This suggests the PMTiles file is valid but the specific tile " + zoom + "/" + x
                            + "/" + y + " does not exist.");
                } else {
                    System.out.println(
                            "Test tile not found either. This suggests potential issues with this zoom level.");
                }

                // If debug search is enabled, try exhaustive search
                if (debugSearch) {
                    // Enable Y flipping automatically in debug mode for better diagnostics
                    if (!tryFlippedY) {
                        System.out.println("\nAutomatically enabling Y-flipping in debug mode...");
                        int flippedY = (numTilesAtZoom - 1) - y;
                        System.out.println("Converting Y coordinate " + y + " to flipped Y coordinate " + flippedY
                                + " (TMS style)");

                        tileData = reader.getTile(zoom, x, flippedY);

                        if (tileData.isPresent()) {
                            System.out.println("Success! Tile found with flipped Y coordinate: " + zoom + "/" + x + "/"
                                    + flippedY);
                            // Update y to the flipped value for the rest of the processing
                            y = flippedY;

                            // Skip the exhaustive search since we found the tile
                            System.out.println("Successfully found tile!");

                            // Determine the output file
                            Path outputPath;
                            if (outputFile != null) {
                                outputPath = outputFile.toPath();
                            } else {
                                String extension = tileTypeToExtension(header.tileType());
                                outputPath = Path.of("tile_" + zoom + "_" + x + "_" + flippedY + extension);
                            }

                            // Write the tile data to file
                            Files.write(
                                    outputPath,
                                    tileData.get(),
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING);

                            System.out.println(
                                    "Extracted tile " + zoom + "/" + x + "/" + flippedY + " to " + outputPath);
                            System.out.println("Tile size: " + tileData.get().length + " bytes");

                            return 0;
                        } else {
                            System.out.println("Tile not found with flipped Y coordinate either.");
                        }
                    }

                    System.out.println(
                            "\nPerforming exhaustive search to find closest tile (this may take some time)...");
                    ZXY zxy = new ZXY((byte) (int) zoom, x, y);
                    long tileId = zxy.toTileId();
                    Long closestTileId = reader.findClosestTileId(tileId);

                    if (closestTileId != null) {
                        ZXY closestZXY = ZXY.fromTileId(closestTileId);
                        System.out.println("\nClosest tile found:");
                        System.out.println(
                                "  Original requested: " + zoom + "/" + x + "/" + y + " (tileId: " + tileId + ")");
                        System.out.println("  Closest available:  " + closestZXY.z() + "/" + closestZXY.x() + "/"
                                + closestZXY.y() + " (tileId: " + closestTileId + ")");

                        System.out.println("\nYou may want to try extracting this tile instead.");
                    }
                }

                System.err.println("Error: Tile " + zoom + "/" + x + "/" + y + " not found in the PMTiles file.");
                return 1;
            }

            System.out.println("Successfully found tile!");
        } catch (Exception e) {
            System.err.println("Error retrieving tile: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }

        // Determine the output file
        Path outputPath;
        if (outputFile != null) {
            outputPath = outputFile.toPath();
        } else {
            String extension = tileTypeToExtension(header.tileType());
            outputPath = Path.of("tile_" + zoom + "_" + x + "_" + y + extension);
        }

        // Write the tile data to file
        Files.write(outputPath, tileData.get(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Extracted tile " + zoom + "/" + x + "/" + y + " to " + outputPath);
        System.out.println("Tile size: " + tileData.get().length + " bytes");

        return 0;
    }

    /**
     * Counts the number of tiles at each zoom level.
     */
    private void countTilesPerZoom(PMTilesReader reader)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        PMTilesHeader header = reader.getHeader();

        for (int z = header.minZoom(); z <= header.maxZoom(); z++) {
            AtomicInteger count = new AtomicInteger();

            // Stream tiles at this zoom level
            final int currentZoom = z;
            reader.streamTiles(currentZoom, tile -> count.incrementAndGet());

            System.out.println("  Zoom " + z + ": " + count.get() + " tiles");
        }
    }

    /**
     * Formats a file size in bytes to a human-readable string.
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Formats a JSON string with proper indentation.
     */
    private String formatJson(String json) {
        // This is a simple formatting that works for well-formed JSON
        // In a real implementation, we'd use a JSON library
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inQuote = false;

        for (char c : json.toCharArray()) {
            if (c == '"' && (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\\')) {
                inQuote = !inQuote;
            }

            if (!inQuote) {
                if (c == '{' || c == '[') {
                    sb.append(c);
                    sb.append('\n');
                    indent += 2;
                    appendIndent(sb, indent);
                } else if (c == '}' || c == ']') {
                    sb.append('\n');
                    indent -= 2;
                    appendIndent(sb, indent);
                    sb.append(c);
                } else if (c == ',') {
                    sb.append(c);
                    sb.append('\n');
                    appendIndent(sb, indent);
                } else if (c == ':') {
                    sb.append(c).append(' ');
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
    }

    private String compressionTypeToString(byte compressionType) {
        return switch (compressionType) {
            case PMTilesHeader.COMPRESSION_NONE -> "None";
            case PMTilesHeader.COMPRESSION_GZIP -> "GZIP";
            case PMTilesHeader.COMPRESSION_BROTLI -> "Brotli";
            case PMTilesHeader.COMPRESSION_ZSTD -> "Zstandard";
            default -> "Unknown (" + compressionType + ")";
        };
    }

    private String tileTypeToString(byte tileType) {
        return switch (tileType) {
            case PMTilesHeader.TILETYPE_MVT -> "MVT (Vector Tile)";
            case PMTilesHeader.TILETYPE_PNG -> "PNG";
            case PMTilesHeader.TILETYPE_JPEG -> "JPEG";
            case PMTilesHeader.TILETYPE_WEBP -> "WebP";
            default -> "Unknown (" + tileType + ")";
        };
    }

    private String tileTypeToExtension(byte tileType) {
        return switch (tileType) {
            case PMTilesHeader.TILETYPE_MVT -> ".mvt";
            case PMTilesHeader.TILETYPE_PNG -> ".png";
            case PMTilesHeader.TILETYPE_JPEG -> ".jpg";
            case PMTilesHeader.TILETYPE_WEBP -> ".webp";
            default -> ".bin";
        };
    }
}
