package io.tileverse.core;

/**
 * Represents a directory entry in a PMTiles file.
 * <p>
 * Each entry maps a tile ID to its location and size in the file, and optionally
 * indicates a run of consecutive tiles that share the same content.
 */
public record PMTilesEntry(
        /**
         * The ID of the tile or the start of a run of tiles.
         * Tile IDs uniquely identify tiles using a Z-order curve value derived
         * from the tile's Z/X/Y coordinates. This allows for efficient binary search
         * and ensures entries are ordered.
         */
        long tileId,

        /**
         * The offset of the tile data or leaf directory in the file.
         * <p>
         * For regular entries, this is the offset within the tile data section,
         * relative to the tileDataOffset in the header.
         * <p>
         * For leaf directory entries, this is the offset within the leaf directories section,
         * relative to the leafDirsOffset in the header.
         */
        long offset,

        /**
         * The length of the tile data or leaf directory in bytes.
         * <p>
         * A length of 0 indicates an empty entry (no tile data).
         */
        int length,

        /**
         * The number of consecutive tiles with the same content.
         * <p>
         * A runLength of 0 indicates this is a leaf directory entry.
         * A runLength of 1 indicates a single tile.
         * A runLength greater than 1 indicates a run of tiles that share the same content,
         * starting at the tileId and continuing for runLength consecutive tile IDs.
         * <p>
         * Runs are used to efficiently represent repeated tiles, such as ocean or
         * empty areas in maps.
         */
        int runLength)
        implements Comparable<PMTilesEntry> {

    /**
     * Creates a new PMTilesEntry with a runLength of 1.
     *
     * @param tileId the tile ID
     * @param offset the offset of the tile data in the file
     * @param length the length of the tile data
     * @return a new PMTilesEntry
     */
    public static PMTilesEntry of(long tileId, long offset, int length) {
        return new PMTilesEntry(tileId, offset, length, 1);
    }

    /**
     * Creates a new PMTilesEntry with the specified runLength.
     *
     * @param tileId the tile ID
     * @param offset the offset of the tile data in the file
     * @param length the length of the tile data
     * @param runLength the number of consecutive tiles with the same content
     * @return a new PMTilesEntry
     */
    public static PMTilesEntry of(long tileId, long offset, int length, int runLength) {
        return new PMTilesEntry(tileId, offset, length, runLength);
    }

    /**
     * Creates a new leaf directory entry.
     * Leaf directory entries have a runLength of 0 to indicate they point to a subdirectory.
     *
     * @param tileId the tile ID
     * @param offset the offset of the subdirectory in the file
     * @param length the length of the subdirectory
     * @return a new PMTilesEntry for a leaf directory
     */
    public static PMTilesEntry leaf(long tileId, long offset, int length) {
        return new PMTilesEntry(tileId, offset, length, 0);
    }

    /**
     * Checks if this entry is a leaf directory entry.
     *
     * @return true if this is a leaf directory entry, false otherwise
     */
    public boolean isLeaf() {
        return runLength == 0;
    }

    /**
     * Checks if this entry is empty.
     * Empty entries are used to represent missing tiles.
     *
     * @return true if this entry is empty, false otherwise
     */
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * Creates a new empty entry.
     * Empty entries are used to represent missing tiles.
     *
     * @return a new empty PMTilesEntry
     */
    public static PMTilesEntry empty() {
        return new PMTilesEntry(0, 0, 0, 0);
    }

    /**
     * Compares this entry with another entry based on tile ID.
     *
     * @param other the entry to compare with
     * @return a negative integer, zero, or a positive integer as this entry's tile ID
     *         is less than, equal to, or greater than the other entry's tile ID
     */
    @Override
    public int compareTo(PMTilesEntry other) {
        return Long.compare(this.tileId, other.tileId);
    }
}
