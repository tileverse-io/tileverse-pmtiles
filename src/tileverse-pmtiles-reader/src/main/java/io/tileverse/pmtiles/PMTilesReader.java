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
package io.tileverse.pmtiles;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.file.FileRangeReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reader for PMTiles files that provides access to tiles and metadata.
 * <p>
 * This class implements the PMTiles format specification, providing a clean API
 * for accessing tile data, metadata, and directory structures within a PMTiles file.
 * <p>
 * It relies on the {@link RangeReader} interface for random access to the underlying
 * data source, allowing for efficient reading from local files, HTTP servers, or cloud storage.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a RangeReader for the desired source
 * RangeReader reader = new FileRangeReader(Path.of("/path/to/tiles.pmtiles"));
 *
 * // For cloud storage or HTTP, create the appropriate RangeReader
 * // RangeReader reader = RangeReaderFactory.create(URI.create("s3://bucket/tiles.pmtiles"));
 *
 * // For performance optimization with cloud storage, use block alignment and caching
 * // RangeReader reader = RangeReaderFactory.createBlockAlignedCaching(
 * //     RangeReaderFactory.create(URI.create("s3://bucket/tiles.pmtiles")));
 *
 * // Create the PMTilesReader with the RangeReader
 * try (PMTilesReader pmtiles = new PMTilesReader(reader)) {
 *     // Access tiles, metadata, etc.
 *     Optional<byte[]> tile = pmtiles.getTile(10, 885, 412);
 * }
 * }</pre>
 */
public class PMTilesReader implements Closeable {
    private final RangeReader rangeReader;
    private final PMTilesHeader header;

    /**
     * Creates a new PMTilesReader for the specified file.
     *
     * @param path the path to the PMTiles file
     * @throws IOException if an I/O error occurs
     * @throws InvalidHeaderException if the file has an invalid header
     */
    public PMTilesReader(Path path) throws IOException, InvalidHeaderException {
        this(FileRangeReader.of(path));
    }

    /**
     * Creates a new PMTilesReader using the specified RangeReader.
     * <p>
     * This constructor allows reading PMTiles from any source that implements the
     * RangeReader interface, such as local files, HTTP servers, or cloud storage.
     *
     * @param rangeReader the range reader to use
     * @throws IOException if an I/O error occurs
     * @throws InvalidHeaderException if the file has an invalid header
     */
    public PMTilesReader(RangeReader rangeReader) throws IOException, InvalidHeaderException {
        this.rangeReader = Objects.requireNonNull(rangeReader, "RangeReader cannot be null");

        // Read the header
        ByteBuffer headerBuffer = rangeReader.readRange(0, 127);
        if (headerBuffer.remaining() != 127) {
            throw new InvalidHeaderException("Failed to read complete header");
        }

        // Deserialize the header directly from the ByteBuffer
        this.header = PMTilesHeader.deserialize(headerBuffer);
    }

    /**
     * Gets the header of the PMTiles file.
     *
     * @return the PMTiles header
     */
    public PMTilesHeader getHeader() {
        return header;
    }

    /**
     * Exhaustively searches all directory entries to find the closest tile ID.
     * This is a debugging method and should not be used in production.
     *
     * @param targetTileId the tile ID to find
     * @return the closest matching tile ID found, or null if none found
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    public Long findClosestTileId(long targetTileId)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        System.out.println("[DEBUG exhaustiveSearch] Exhaustively searching for closest tile ID to " + targetTileId);

        // Get all entries
        List<PMTilesEntry> allEntries = getAllEntries();

        // Sort them by tileId
        allEntries.sort((a, b) -> Long.compare(a.tileId(), b.tileId()));

        System.out.println("[DEBUG exhaustiveSearch] Searching through " + allEntries.size() + " entries");

        Long closestTileId = null;
        long minDistance = Long.MAX_VALUE;

        for (PMTilesEntry entry : allEntries) {
            // Skip leaf directories
            if (entry.isLeaf()) {
                continue;
            }

            // Skip empty entries
            if (entry.isEmpty()) {
                continue;
            }

            // Check if this tileId is in range
            long entryStartTileId = entry.tileId();
            long entryEndTileId = entryStartTileId + entry.runLength() - 1;

            if (targetTileId >= entryStartTileId && targetTileId <= entryEndTileId) {
                System.out.println("[DEBUG exhaustiveSearch] Found exact match: " + entry.tileId() + " (runs from "
                        + entryStartTileId + " to " + entryEndTileId + ")");
                return targetTileId;
            }

            // Check if this is the closest match so far
            long distanceToStart = Math.abs(targetTileId - entryStartTileId);
            long distanceToEnd = Math.abs(targetTileId - entryEndTileId);
            long distance = Math.min(distanceToStart, distanceToEnd);

            if (distance < minDistance) {
                minDistance = distance;
                closestTileId = distance == distanceToStart ? entryStartTileId : entryEndTileId;
            }
        }

        if (closestTileId != null) {
            System.out.println("[DEBUG exhaustiveSearch] Closest tile ID found: " + closestTileId + " (distance: "
                    + minDistance + ")");
            // Find the entry for this tile ID
            for (PMTilesEntry entry : allEntries) {
                long entryStartTileId = entry.tileId();
                long entryEndTileId = entryStartTileId + entry.runLength() - 1;

                if (closestTileId >= entryStartTileId && closestTileId <= entryEndTileId) {
                    ZXY zxy = ZXY.fromTileId(closestTileId);
                    System.out.println("[DEBUG exhaustiveSearch]   This corresponds to tile: " + zxy.z() + "/" + zxy.x()
                            + "/" + zxy.y());
                    break;
                }
            }
        } else {
            System.out.println("[DEBUG exhaustiveSearch] No closest tile ID found");
        }

        return closestTileId;
    }

    /**
     * Gets a tile by its ZXY coordinates.
     *
     * @param z the zoom level
     * @param x the X coordinate
     * @param y the Y coordinate
     * @return the tile data, or empty if the tile doesn't exist
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    public Optional<byte[]> getTile(int z, int x, int y)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        System.out.println("[DEBUG getTile] Requested tile: " + z + "/" + x + "/" + y);
        System.out.println("[DEBUG getTile] PMTiles min/max zoom: " + header.minZoom() + "/" + header.maxZoom());

        ZXY zxy = new ZXY((byte) z, x, y);
        long tileId = zxy.toTileId();

        System.out.println("[DEBUG getTile] Converted to tileId: " + tileId);

        // Validate against header
        int maxTilesWide = 1 << z;
        System.out.println("[DEBUG getTile] Valid ranges at z=" + z + ": x=[0," + (maxTilesWide - 1) + "], y=[0,"
                + (maxTilesWide - 1) + "]");

        // Find the tile in the directory structure
        Optional<TileLocation> location = findTileLocation(tileId);
        if (location.isEmpty()) {
            System.out.println("[DEBUG getTile] No location found for tileId " + tileId);
            return Optional.empty();
        }

        // Read the tile data
        TileLocation tileLocation = location.get();
        long offset = header.tileDataOffset() + tileLocation.offset();
        int length = tileLocation.length();

        System.out.println("[DEBUG getTile] Found tile at offset=" + offset + ", length=" + length);

        ByteBuffer buffer = rangeReader.readRange(offset, length);

        System.out.println("[DEBUG getTile] Successfully read " + buffer.remaining() + " bytes from RangeReader");

        byte[] tileData = new byte[buffer.remaining()];
        buffer.get(tileData);

        // Decompress if necessary
        if (header.tileCompression() != PMTilesHeader.COMPRESSION_NONE) {
            byte[] before = tileData;
            tileData = CompressionUtil.decompress(tileData, header.tileCompression());
            System.out.println(
                    "[DEBUG getTile] Decompressed from " + before.length + " to " + tileData.length + " bytes");
        }

        System.out.println("[DEBUG getTile] Successfully retrieved tile data (" + tileData.length + " bytes)");
        return Optional.of(tileData);
    }

    /**
     * Gets the metadata JSON from the PMTiles file.
     *
     * @return the metadata as a byte array
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    public byte[] getMetadata() throws IOException, CompressionUtil.UnsupportedCompressionException {
        long offset = header.jsonMetadataOffset();
        long length = header.jsonMetadataBytes();

        ByteBuffer buffer = rangeReader.readRange(offset, (int) length);

        byte[] metadataBytes = new byte[buffer.remaining()];
        buffer.get(metadataBytes);

        // Decompress if necessary
        if (header.internalCompression() != PMTilesHeader.COMPRESSION_NONE) {
            metadataBytes = CompressionUtil.decompress(metadataBytes, header.internalCompression());
        }

        return metadataBytes;
    }

    /**
     * Streams all tiles at a specific zoom level, calling the provided consumer for each tile.
     *
     * @param zoom the zoom level
     * @param consumer the consumer to call for each tile
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    public void streamTiles(int zoom, Consumer<Tile> consumer)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        // Get all entries
        List<PMTilesEntry> entries = getAllEntries();

        // Filter by zoom level and convert to Tiles
        for (PMTilesEntry entry : entries) {
            // Skip leaf directory entries
            if (entry.isLeaf()) {
                continue;
            }

            // Process each tile in the run
            for (int i = 0; i < entry.runLength(); i++) {
                long tileId = entry.tileId() + i;
                ZXY zxy = ZXY.fromTileId(tileId);

                // Skip tiles not at the requested zoom
                if (zxy.z() != zoom) {
                    continue;
                }

                // Read the tile data
                long offset = header.tileDataOffset() + entry.offset();
                int length = entry.length();

                ByteBuffer buffer = rangeReader.readRange(offset, length);

                byte[] tileData = new byte[buffer.remaining()];
                buffer.get(tileData);

                // Decompress if necessary
                if (header.tileCompression() != PMTilesHeader.COMPRESSION_NONE) {
                    tileData = CompressionUtil.decompress(tileData, header.tileCompression());
                }

                consumer.accept(new Tile(zxy.z(), zxy.x(), zxy.y(), tileData));
            }
        }
    }

    /**
     * Gets all directory entries in the PMTiles file.
     *
     * @return a list of all PMTilesEntry objects
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    public List<PMTilesEntry> getAllEntries() throws IOException, CompressionUtil.UnsupportedCompressionException {
        List<PMTilesEntry> allEntries = new ArrayList<>();

        // Read the root directory
        byte[] rootDirBytes = readDirectoryBytes(header.rootDirOffset(), (int) header.rootDirBytes());
        List<PMTilesEntry> rootEntries = DirectoryUtil.deserializeDirectory(rootDirBytes);

        // Process each entry in the root directory
        for (PMTilesEntry entry : rootEntries) {
            if (entry.isLeaf()) {
                // This is a leaf directory, read and process its entries
                byte[] leafDirBytes = readDirectoryBytes(header.leafDirsOffset() + entry.offset(), entry.length());
                List<PMTilesEntry> leafEntries = DirectoryUtil.deserializeDirectory(leafDirBytes);
                allEntries.addAll(leafEntries);
            } else {
                // This is a regular entry
                allEntries.add(entry);
            }
        }

        return allEntries;
    }

    /**
     * Finds the location of a tile in the PMTiles file.
     *
     * @param tileId the ID of the tile to find
     * @return the location of the tile, or empty if the tile doesn't exist
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    private Optional<TileLocation> findTileLocation(long tileId)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        System.out.println("[DEBUG findTileLocation] Looking for tileId: " + tileId);

        // Read the root directory
        byte[] rootDirBytes = readDirectoryBytes(header.rootDirOffset(), (int) header.rootDirBytes());
        List<PMTilesEntry> rootEntries = DirectoryUtil.deserializeDirectory(rootDirBytes);

        System.out.println("[DEBUG findTileLocation] Root directory contains " + rootEntries.size() + " entries");

        // Print some sample entries from the root directory
        if (!rootEntries.isEmpty()) {
            int samplesToShow = Math.min(5, rootEntries.size());
            System.out.println("[DEBUG findTileLocation] Sample root entries (showing " + samplesToShow + " of "
                    + rootEntries.size() + "):");
            for (int i = 0; i < samplesToShow; i++) {
                PMTilesEntry e = rootEntries.get(i);
                System.out.println("[DEBUG findTileLocation]   Entry " + i + ": tileId=" + e.tileId() + ", runLength="
                        + e.runLength() + ", isLeaf="
                        + e.isLeaf() + ", offset="
                        + e.offset() + ", length="
                        + e.length());
            }
        }

        // Find the entry for this tile ID in the root directory
        System.out.println("[DEBUG findTileLocation] Searching for tileId " + tileId + " in root directory...");
        Optional<PMTilesEntry> rootEntry = findEntryForTileId(rootEntries, tileId);

        if (rootEntry.isEmpty()) {
            System.out.println(
                    "[DEBUG findTileLocation] No matching entry found in root directory for tileId " + tileId);
            return Optional.empty();
        }

        PMTilesEntry entry = rootEntry.get();
        System.out.println("[DEBUG findTileLocation] Found matching entry in root directory: " + "tileId="
                + entry.tileId() + ", runLength="
                + entry.runLength() + ", isLeaf="
                + entry.isLeaf() + ", offset="
                + entry.offset() + ", length="
                + entry.length());

        if (entry.isLeaf()) {
            // This is a leaf directory, read it and find the tile
            System.out.println("[DEBUG findTileLocation] Entry is a leaf directory, reading leaf entries...");
            byte[] leafDirBytes = readDirectoryBytes(header.leafDirsOffset() + entry.offset(), entry.length());
            List<PMTilesEntry> leafEntries = DirectoryUtil.deserializeDirectory(leafDirBytes);

            System.out.println("[DEBUG findTileLocation] Leaf directory contains " + leafEntries.size() + " entries");

            // Print some sample entries from the leaf directory
            if (!leafEntries.isEmpty()) {
                int samplesToShow = Math.min(5, leafEntries.size());
                System.out.println("[DEBUG findTileLocation] Sample leaf entries (showing " + samplesToShow + " of "
                        + leafEntries.size() + "):");
                for (int i = 0; i < samplesToShow; i++) {
                    PMTilesEntry e = leafEntries.get(i);
                    System.out.println(
                            "[DEBUG findTileLocation]   Entry " + i + ": tileId=" + e.tileId() + ", runLength="
                                    + e.runLength() + ", isLeaf="
                                    + e.isLeaf() + ", offset="
                                    + e.offset() + ", length="
                                    + e.length());
                }
            }

            // Find the entry for this tile ID in the leaf directory
            System.out.println("[DEBUG findTileLocation] Searching for tileId " + tileId + " in leaf directory...");
            Optional<PMTilesEntry> leafEntry = findEntryForTileId(leafEntries, tileId);

            if (leafEntry.isEmpty()) {
                System.out.println(
                        "[DEBUG findTileLocation] No matching entry found in leaf directory for tileId " + tileId);
                return Optional.empty();
            }

            entry = leafEntry.get();
            System.out.println("[DEBUG findTileLocation] Found matching entry in leaf directory: " + "tileId="
                    + entry.tileId() + ", runLength="
                    + entry.runLength() + ", isLeaf="
                    + entry.isLeaf() + ", offset="
                    + entry.offset() + ", length="
                    + entry.length());
        }

        // Check if we have a valid entry
        if (entry.isEmpty()) {
            System.out.println("[DEBUG findTileLocation] Entry is empty, no tile data exists");
            return Optional.empty();
        }

        System.out.println("[DEBUG findTileLocation] Returning tile location: offset=" + entry.offset() + ", length="
                + entry.length());
        return Optional.of(new TileLocation(entry.offset(), entry.length()));
    }

    /**
     * Finds an entry for a specific tile ID in a list of entries.
     *
     * @param entries the list of entries to search
     * @param tileId the tile ID to find
     * @return the entry for the tile ID, or empty if not found
     */
    private Optional<PMTilesEntry> findEntryForTileId(List<PMTilesEntry> entries, long tileId) {
        System.out.println(
                "[DEBUG findEntryForTileId] Searching for tileId " + tileId + " in " + entries.size() + " entries");

        // Binary search for the entry
        int low = 0;
        int high = entries.size() - 1;
        int iterations = 0;

        // Keep track of the closest leaf entry we find
        PMTilesEntry closestLeafEntry = null;
        long closestLeafDistance = Long.MAX_VALUE;

        while (low <= high) {
            iterations++;
            int mid = (low + high) >>> 1;
            PMTilesEntry entry = entries.get(mid);

            // Handle leaf entries specially - they have runLength = 0 which causes issues
            if (entry.isLeaf()) {
                long distanceToThisLeaf = Math.abs(tileId - entry.tileId());

                // Track the closest leaf entry we find during search
                if (distanceToThisLeaf < closestLeafDistance) {
                    closestLeafDistance = distanceToThisLeaf;
                    closestLeafEntry = entry;
                }

                System.out.println(
                        "[DEBUG findEntryForTileId] Iteration " + iterations + ": checking leaf entry at index "
                                + mid + ", tileId="
                                + entry.tileId() + ", isLeaf=true");

                // For leaf entries, we don't have a range - just compare the tileId directly
                if (tileId < entry.tileId()) {
                    System.out.println("[DEBUG findEntryForTileId]   tileId " + tileId
                            + " is less than leaf entry tileId " + entry.tileId() + ", searching left");
                    high = mid - 1;
                } else if (tileId > entry.tileId()) {
                    System.out.println("[DEBUG findEntryForTileId]   tileId " + tileId
                            + " is greater than leaf entry tileId " + entry.tileId() + ", searching right");
                    low = mid + 1;
                } else {
                    // Exact match on the tileId
                    System.out.println("[DEBUG findEntryForTileId]   Found exact match with leaf entry!");
                    return Optional.of(entry);
                }
            } else {
                // Regular (non-leaf) entry handling
                long entryStart = entry.tileId();
                long entryEnd = entry.tileId() + entry.runLength() - 1;

                System.out.println("[DEBUG findEntryForTileId] Iteration " + iterations + ": checking entry at index "
                        + mid + ", tileId range "
                        + entryStart + " to " + entryEnd + ", isLeaf="
                        + entry.isLeaf());

                if (tileId < entryStart) {
                    System.out.println("[DEBUG findEntryForTileId]   tileId " + tileId + " is less than entry start "
                            + entryStart + ", searching left");
                    high = mid - 1;
                } else if (tileId > entryEnd) {
                    System.out.println("[DEBUG findEntryForTileId]   tileId " + tileId + " is greater than entry end "
                            + entryEnd + ", searching right");
                    low = mid + 1;
                } else {
                    // Found a match
                    System.out.println("[DEBUG findEntryForTileId]   Found match! tileId " + tileId
                            + " is within range " + entryStart + " to " + entryEnd);
                    return Optional.of(entry);
                }
            }
        }

        // If we've found a leaf entry that's close to our target tileId,
        // return it since it might contain the subdirectory with our target
        if (closestLeafEntry != null && closestLeafDistance < 10000) {
            System.out.println("[DEBUG findEntryForTileId] Returning closest leaf entry with tileId="
                    + closestLeafEntry.tileId() + " (distance=" + closestLeafDistance + ")");
            return Optional.of(closestLeafEntry);
        }

        System.out.println("[DEBUG findEntryForTileId] No match found after " + iterations + " iterations (low=" + low
                + ", high=" + high + ")");
        return Optional.empty();
    }

    /**
     * Reads directory bytes from the file.
     *
     * @param offset the offset to read from
     * @param length the number of bytes to read
     * @return the directory bytes, decompressed if necessary
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    private byte[] readDirectoryBytes(long offset, int length)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        ByteBuffer buffer = rangeReader.readRange(offset, length);

        byte[] directoryBytes = new byte[buffer.remaining()];
        buffer.get(directoryBytes);

        // Decompress if necessary
        if (header.internalCompression() != PMTilesHeader.COMPRESSION_NONE) {
            directoryBytes = CompressionUtil.decompress(directoryBytes, header.internalCompression());
        }

        return directoryBytes;
    }

    @Override
    public void close() throws IOException {
        rangeReader.close();
    }

    /**
     * Represents the location of a tile in the PMTiles file.
     *
     * @param offset the offset of the tile data
     * @param length the length of the tile data
     */
    private record TileLocation(long offset, int length) {}

    /**
     * Represents a tile with its coordinates and data.
     *
     * @param z the zoom level
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param data the tile data
     */
    public record Tile(byte z, int x, int y, byte[] data) {}
}
