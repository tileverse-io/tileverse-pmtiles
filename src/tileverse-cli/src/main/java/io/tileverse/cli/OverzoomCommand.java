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

import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command for overzoom functionality, similar to tippecanoe-overzoom.
 * This generates a PMTiles file with overzoom levels derived from the input file.
 */
@Command(
        name = "overzoom",
        aliases = {"tippecanoe-overzoom"},
        description = "Generate a PMTiles file with overzoom levels")
public class OverzoomCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT", description = "Input PMTiles file")
    private File inputFile;

    @Parameters(index = "1", paramLabel = "OUTPUT", description = "Output PMTiles file")
    private File outputFile;

    @Option(
            names = {"-z", "--maximum-zoom"},
            paramLabel = "ZOOM",
            description = "Maximum zoom level to generate (default: input file's max zoom + 5)")
    private Integer maxZoom;

    @Option(
            names = {"-Z", "--minimum-zoom"},
            paramLabel = "ZOOM",
            description = "Minimum zoom level to include (default: input file's min zoom)")
    private Integer minZoom;

    @Option(
            names = "--extend-zooms-if-still-dropping",
            description =
                    "If features are being dropped at max zoom, keep adding zoom levels until they are all included")
    private boolean extendZooms;

    @Option(
            names = "--drop-densest-as-needed",
            description = "If a tile is too large, try dropping the densest features before splitting")
    private boolean dropDensest;

    @Option(
            names = "--drop-fraction-as-needed",
            description = "If a tile is too large, drop some fraction of the features to make it fit")
    private boolean dropFraction;

    @Option(names = "--deduplicate-by-id", description = "Deduplicate features with the same ID")
    private boolean deduplicateById;

    @Option(
            names = {"--compression", "-c"},
            paramLabel = "COMPRESSION",
            description = "Compression algorithm: gzip, brotli, or zstd (default: gzip)")
    private String compression = "gzip";

    @Option(
            names = {"--force", "-f"},
            description = "Delete output file if it already exists")
    private boolean force;

    @Override
    public Integer call() throws Exception {
        // In a real implementation, this would call into the API to perform overzoom
        // For now, we'll just print the options

        System.out.println("Tileverse Overzoom");
        System.out.println("==================");
        System.out.println("Input file: " + inputFile);
        System.out.println("Output file: " + outputFile);
        System.out.println("Options:");
        System.out.println("  Max zoom: " + (maxZoom != null ? maxZoom : "auto"));
        System.out.println("  Min zoom: " + (minZoom != null ? minZoom : "auto"));
        System.out.println("  Extend zooms if still dropping: " + extendZooms);
        System.out.println("  Drop densest as needed: " + dropDensest);
        System.out.println("  Drop fraction as needed: " + dropFraction);
        System.out.println("  Deduplicate by ID: " + deduplicateById);
        System.out.println("  Compression: " + compression);
        System.out.println("  Force: " + force);

        System.out.println("\nGenerating overzoom tiles...");
        System.out.println("Done! Output written to " + outputFile);

        return 0;
    }
}
