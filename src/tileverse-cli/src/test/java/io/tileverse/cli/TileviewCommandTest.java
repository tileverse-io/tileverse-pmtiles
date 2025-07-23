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

import static org.junit.jupiter.api.Assertions.*;

import io.tileverse.pmtiles.CompressionUtil;
import io.tileverse.pmtiles.InvalidHeaderException;
import io.tileverse.pmtiles.PMTilesHeader;
import io.tileverse.pmtiles.PMTilesReader;
import io.tileverse.pmtiles.PMTilesWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Tests for the TileviewCommand class.
 */
public class TileviewCommandTest {

    @TempDir
    Path tempDir;

    private Path testPMTilesPath;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    public void setUp() throws IOException {
        // Set up a test PMTiles file
        testPMTilesPath = tempDir.resolve("test.pmtiles");
        createTestPMTilesFile(testPMTilesPath);

        // Redirect stdout and stderr for testing
        System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    public void restoreStreams() {
        // Reset the output streams after each test
        System.setOut(originalOut);
        System.setErr(originalErr);
        outContent.reset();
        errContent.reset();
    }

    @Test
    public void testSummaryDisplay() {
        // Create the command
        TileviewCommand command = new TileviewCommand();
        CommandLine cmdLine = new CommandLine(command);

        // Execute with the test file
        int exitCode = cmdLine.execute(testPMTilesPath.toString(), "--summary");

        // Check exit code
        assertEquals(0, exitCode);

        // Check output
        String output = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("PMTiles Summary:"));
        assertTrue(output.contains("Zoom levels: 0 to 1"));
        assertTrue(output.contains("Tile count: 2"));
    }

    @Test
    public void testMetadataDisplay() {
        // Create the command
        TileviewCommand command = new TileviewCommand();
        CommandLine cmdLine = new CommandLine(command);

        // Execute with the test file
        int exitCode = cmdLine.execute(testPMTilesPath.toString(), "--metadata");

        // Check exit code
        assertEquals(0, exitCode);

        // Check output
        String output = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("PMTiles Header:"));
        assertTrue(output.contains("Min zoom: 0"));
        assertTrue(output.contains("Max zoom: 1"));
        assertTrue(output.contains("Metadata JSON:"));
        assertTrue(output.contains("\"name\": \"Test Tileset\""));
    }

    @Test
    public void testTileDisplay() {
        // Create the command
        TileviewCommand command = new TileviewCommand();
        CommandLine cmdLine = new CommandLine(command);

        // Execute with the test file
        int exitCode = cmdLine.execute(testPMTilesPath.toString(), "-z", "0", "-x", "0", "-y", "0");

        // Check exit code
        assertEquals(0, exitCode);

        // Check output
        String output = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Tile 0/0/0:"));
        assertTrue(output.contains("Size:"));
    }

    @Test
    public void testTileNotFound() {
        // Create the command
        TileviewCommand command = new TileviewCommand();
        CommandLine cmdLine = new CommandLine(command);

        // Execute with the test file and non-existent tile
        int exitCode = cmdLine.execute(testPMTilesPath.toString(), "-z", "10", "-x", "5", "-y", "5");

        // Check exit code
        assertEquals(1, exitCode);

        // Check error output (error messages go to stderr)
        String errorOutput = errContent.toString(StandardCharsets.UTF_8);
        assertTrue(errorOutput.contains("Error: Zoom level 10 is outside the valid range 0 to 1"));
    }

    @Test
    public void testExtractTile()
            throws IOException, InvalidHeaderException, CompressionUtil.UnsupportedCompressionException {
        // Create the command
        TileviewCommand command = new TileviewCommand();
        CommandLine cmdLine = new CommandLine(command);

        // Path for extracted tile
        Path extractPath = tempDir.resolve("extracted_tile.mvt");

        // Execute with the test file
        int exitCode = cmdLine.execute(
                testPMTilesPath.toString(), "--extract", "-z", "0", "-x", "0", "-y", "0", "-o", extractPath.toString());

        // Check exit code
        assertEquals(0, exitCode);

        // Check output
        String output = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Extracted tile 0/0/0 to"));

        // Verify the file was created
        assertTrue(extractPath.toFile().exists());

        // Verify the contents
        try (PMTilesReader reader = new PMTilesReader(testPMTilesPath)) {
            Optional<byte[]> expectedData = reader.getTile(0, 0, 0);
            assertTrue(expectedData.isPresent());

            // Compare with extracted data
            byte[] extractedData = java.nio.file.Files.readAllBytes(extractPath);
            assertArrayEquals(expectedData.get(), extractedData);
        }
    }

    @Test
    public void testInvalidFile() {
        // Create the command
        TileviewCommand command = new TileviewCommand();
        CommandLine cmdLine = new CommandLine(command);

        // Create a non-PMTiles file
        Path invalidFile = tempDir.resolve("invalid.txt");
        try {
            java.nio.file.Files.writeString(invalidFile, "This is not a PMTiles file");
        } catch (IOException e) {
            fail("Failed to create test file: " + e.getMessage());
        }

        // Execute with the invalid file
        int exitCode = cmdLine.execute(invalidFile.toString());

        // Check exit code
        assertEquals(1, exitCode);

        // Check error output
        String errorOutput = errContent.toString(StandardCharsets.UTF_8);
        assertTrue(errorOutput.contains("Error: Not a valid PMTiles file"));
    }

    /**
     * Helper method to create a test PMTiles file.
     */
    private void createTestPMTilesFile(Path path) throws IOException {
        // Create test tile data
        byte[] rootTileData = "This is the root tile data".getBytes(StandardCharsets.UTF_8);
        byte[] zoomOneTileData = "This is a zoom level 1 tile".getBytes(StandardCharsets.UTF_8);

        // Create test metadata
        String metadata =
                "{\"name\":\"Test Tileset\",\"version\":\"1.0\",\"description\":\"Test tileset for unit tests\"}";

        // Create a PMTiles file
        try (PMTilesWriter writer = PMTilesWriter.builder()
                .outputPath(path)
                .minZoom((byte) 0)
                .maxZoom((byte) 1)
                .tileCompression(PMTilesHeader.COMPRESSION_NONE) // No compression for easy testing
                .internalCompression(PMTilesHeader.COMPRESSION_NONE)
                .tileType(PMTilesHeader.TILETYPE_MVT)
                .build()) {

            // Add tiles
            writer.addTile((byte) 0, 0, 0, rootTileData);
            writer.addTile((byte) 1, 0, 0, zoomOneTileData);

            // Set metadata
            writer.setMetadata(metadata);

            // Complete the file
            writer.complete();
        }
    }
}
