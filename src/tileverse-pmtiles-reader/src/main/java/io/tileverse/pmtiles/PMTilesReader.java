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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tileverse.jackson.databind.pmtiles.v3.PMTilesMetadata;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.file.FileRangeReader;
import io.tileverse.rangereader.nio.ByteBufferPool;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(PMTilesReader.class);

    private final RangeReader rangeReader;
    private final PMTilesHeader header;
    private final ObjectMapper objectMapper;

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
        this.objectMapper = new ObjectMapper();

        // Read the header
        ByteBuffer headerBuffer = ByteBufferPool.getDefault().borrowHeap(127);
        try {
            if (rangeReader.readRange(0, 127, headerBuffer) != 127) {
                throw new InvalidHeaderException("Failed to read complete header");
            }

            // Deserialize the header directly from the ByteBuffer
            this.header = PMTilesHeader.deserialize(headerBuffer);
        } finally {
            ByteBufferPool.getDefault().returnBuffer(headerBuffer);
        }
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

        ZXY zxy = ZXY.of(z, x, y);
        long tileId = zxy.toTileId();

        // Find the tile in the directory structure
        Optional<TileLocation> location = findTileLocation(tileId);
        if (location.isEmpty()) {
            return Optional.empty();
        }

        // Read the tile data
        TileLocation tileLocation = location.get();
        long offset = header.tileDataOffset() + tileLocation.offset();
        int length = tileLocation.length();

        byte[] tileData;
        ByteBuffer buffer = ByteBufferPool.getDefault().borrowHeap(length);
        try {
            rangeReader.readRange(offset, length, buffer);
            tileData = new byte[buffer.remaining()];
            buffer.get(tileData);
        } finally {
            ByteBufferPool.getDefault().returnBuffer(buffer);
        }
        tileData = CompressionUtil.decompress(tileData, header.tileCompression());
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

        byte[] metadataBytes;
        ByteBuffer buffer = ByteBufferPool.getDefault().borrowHeap((int) length);
        try {
            rangeReader.readRange(offset, (int) length, buffer);
            metadataBytes = new byte[buffer.remaining()];
            buffer.get(metadataBytes);
        } finally {
            ByteBufferPool.getDefault().returnBuffer(buffer);
        }

        // Decompress if necessary
        if (header.internalCompression() != PMTilesHeader.COMPRESSION_NONE) {
            metadataBytes = CompressionUtil.decompress(metadataBytes, header.internalCompression());
        }

        return metadataBytes;
    }

    /**
     * Gets the metadata as a parsed JSON string.
     *
     * @return the metadata as a JSON string
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    public String getMetadataAsString() throws IOException, CompressionUtil.UnsupportedCompressionException {
        byte[] metadataBytes = getMetadata();
        return new String(metadataBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Gets the metadata as a parsed PMTilesMetadata object.
     * This provides structured access to the metadata fields with proper type conversion.
     *
     * @return the metadata as a PMTilesMetadata object
     * @throws IOException if an I/O error occurs or JSON parsing fails
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    public PMTilesMetadata getMetadataObject() throws IOException, CompressionUtil.UnsupportedCompressionException {
        byte[] metadataBytes = getMetadata();
        if (metadataBytes.length == 0) {
            // Return empty metadata if no metadata is present
            return PMTilesMetadata.of(null);
        }

        try {
            return objectMapper.readValue(metadataBytes, PMTilesMetadata.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse PMTiles metadata JSON", e);
        }
    }

    /**
     * Finds the location of a tile in the PMTiles file using recursive directory traversal.
     *
     * @param tileId the ID of the tile to find
     * @return the location of the tile, or empty if the tile doesn't exist
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    private Optional<TileLocation> findTileLocation(long tileId)
            throws IOException, CompressionUtil.UnsupportedCompressionException {
        return searchDirectory(header.rootDirOffset(), (int) header.rootDirBytes(), tileId, false);
    }

    /**
     * Recursively searches directories for a tile entry.
     *
     * @param dirOffset the offset of the directory to search
     * @param dirLength the length of the directory data
     * @param tileId the tile ID to find
     * @param isLeafDir whether this is a leaf directory (determines offset calculation)
     * @return the tile location if found, or empty if not found
     * @throws IOException if an I/O error occurs
     * @throws CompressionUtil.UnsupportedCompressionException if the compression type is not supported
     */
    private Optional<TileLocation> searchDirectory(long dirOffset, int dirLength, long tileId, boolean isLeafDir)
            throws IOException, CompressionUtil.UnsupportedCompressionException {

        // Read and deserialize directory
        byte[] dirBytes = readDirectoryBytes(dirOffset, dirLength);
        List<PMTilesEntry> entries = DirectoryUtil.deserializeDirectory(dirBytes);

        // Find entry that might contain our tileId
        Optional<PMTilesEntry> entry = findEntryForTileId(entries, tileId);

        if (entry.isEmpty()) {
            return Optional.empty();
        }

        PMTilesEntry found = entry.get();

        if (found.isLeaf()) {
            // Recursively search the leaf directory
            return searchDirectory(header.leafDirsOffset() + found.offset(), found.length(), tileId, true);
        } else {
            // This is a tile entry - check if it's empty
            if (found.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new TileLocation(found.offset(), found.length()));
        }
    }

    /**
     * Searches for a directory entry that contains the specified tile ID using binary search.
     * This method handles both regular tile entries (with run lengths) and leaf directory entries.
     *
     * @param entries the list of directory entries to search (must be sorted by tileId)
     * @param tileId the tile ID to search for
     * @return the directory entry that contains the tile, or empty if no suitable entry found
     */
    private Optional<PMTilesEntry> findEntryForTileId(List<PMTilesEntry> entries, long tileId) {
        int low = 0;
        int high = entries.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            PMTilesEntry entry = entries.get(mid);

            if (tileId < entry.tileId()) {
                high = mid - 1;
            } else if (entry.isLeaf() || entry.runLength() == 0) {
                // For leaf entries or entries with no run length, match exact tileId
                if (tileId == entry.tileId()) {
                    return Optional.of(entry);
                } else if (tileId > entry.tileId()) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            } else {
                // For regular entries, check if tileId falls within the run range
                long entryEnd = entry.tileId() + entry.runLength() - 1;
                if (tileId <= entryEnd) {
                    return Optional.of(entry);
                } else {
                    low = mid + 1;
                }
            }
        }

        // No exact match found, check for containing entry at insertion point
        return findContainingEntry(entries, high, tileId);
    }

    /**
     * Attempts to find an entry that contains the target tileId at the binary search insertion point.
     *
     * <p>This method handles the PMTiles format's range-based entries where a single directory entry
     * can represent multiple consecutive tiles. After a binary search fails to find an exact match,
     * the entry just before the insertion point might still contain our target tile.
     *
     * <p>Two cases are handled:
     * <ul>
     * <li><b>Regular entries with run lengths</b>: An entry with tileId=100 and runLength=5 represents
     *     tiles 100-104. If searching for tileId=102, this entry should be returned even though
     *     102 != 100, because 102 falls within the range [100, 104].</li>
     * <li><b>Leaf directory entries</b>: These point to subdirectories that might contain the target
     *     tile. Since we don't know the exact bounds of leaf directories, we return the closest
     *     leaf entry as a heuristic - it might contain our target in its subdirectory.</li>
     * </ul>
     *
     * @param entries the list of directory entries that was searched
     * @param insertionPoint the index where the target would be inserted (from binary search)
     * @param tileId the tile ID we're searching for
     * @return the containing entry if found, or empty if no suitable entry exists
     */
    private Optional<PMTilesEntry> findContainingEntry(List<PMTilesEntry> entries, int insertionPoint, long tileId) {
        if (insertionPoint < 0) {
            return Optional.empty();
        }

        PMTilesEntry candidate = entries.get(insertionPoint);

        if (candidate.isLeaf()) {
            // Return leaf directory - it might contain our tile in its subdirectory
            return Optional.of(candidate);
        } else if (candidate.runLength() > 0) {
            // Check if tileId falls within the entry's run range [tileId, tileId + runLength - 1]
            long rangeEnd = candidate.tileId() + candidate.runLength() - 1;
            if (tileId <= rangeEnd) {
                return Optional.of(candidate);
            }
        }

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

        byte[] directoryBytes;
        ByteBuffer buffer = ByteBufferPool.getDefault().borrowHeap(length);
        try {
            rangeReader.readRange(offset, length, buffer);
            directoryBytes = new byte[buffer.remaining()];
            buffer.get(directoryBytes);
        } finally {
            ByteBufferPool.getDefault().returnBuffer(buffer);
        }
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
