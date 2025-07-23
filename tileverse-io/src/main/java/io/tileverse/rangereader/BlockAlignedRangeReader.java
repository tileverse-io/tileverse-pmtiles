package io.tileverse.rangereader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A decorator for RangeReader that aligns all read requests to fixed-size
 * blocks.
 * <p>
 * This implementation ensures that all reads to the underlying reader are
 * aligned to block boundaries, which helps with caching efficiency. It also
 * prevents overlapping range requests, as all reads will be aligned to the same
 * block boundaries regardless of the original offset requested.
 * <p>
 * When a range is requested that crosses block boundaries, this reader will
 * read all necessary blocks and return only the requested portion.
 */
public class BlockAlignedRangeReader extends AbstractRangeReader implements RangeReader {

    /** Default block size (64 KB) */
    public static final int DEFAULT_BLOCK_SIZE = 64 * 1024;

    private final RangeReader delegate;
    private final int blockSize;

    /**
     * Creates a new BlockAlignedRangeReader with the default block size (64 KB).
     *
     * @param delegate The underlying RangeReader to delegate to
     */
    public BlockAlignedRangeReader(RangeReader delegate) {
        this(delegate, DEFAULT_BLOCK_SIZE);
    }

    /**
     * Creates a new BlockAlignedRangeReader with the specified block size.
     *
     * @param delegate  The underlying RangeReader to delegate to
     * @param blockSize The block size to align reads to, must be a power of 2
     * @throws IllegalArgumentException If blockSize is not a power of 2
     */
    public BlockAlignedRangeReader(RangeReader delegate, int blockSize) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate RangeReader cannot be null");

        // Validate block size is a power of 2
        if (blockSize <= 0 || (blockSize & (blockSize - 1)) != 0) {
            throw new IllegalArgumentException("Block size must be a positive power of 2");
        }

        this.blockSize = blockSize;
    }

    /**
     * Gets the block size used for aligning reads.
     *
     * @return The block size in bytes
     */
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        // Calculate block-aligned range boundaries
        final long blockMask = blockSize - 1;
        final long alignedOffset = offset & ~blockMask;
        final long endOffset = offset + actualLength;
        final long alignedEndOffset = (endOffset + blockMask) & ~blockMask;

        // Calculate number of blocks we need to read, at least one block
        final int numBlocks = Math.max(1, (int) ((alignedEndOffset - alignedOffset) / blockSize));

        if (numBlocks == 1 && target.remaining() >= blockSize) {
            final int initialPosition = target.position();
            final int blockOffset = (int) (offset - alignedOffset);
            int readCount = delegate.readRange(alignedOffset, blockSize, target);
            int read = Math.min(readCount - blockOffset, actualLength);
            target.position(initialPosition + read);
            return read;
        }

        // Keep a small working buffer to read blocks
        final ByteBuffer blockBuffer = ByteBuffer.allocate(blockSize);

        // Position tracking for partial blocks
        int bytesRemaining = actualLength;

        // Read each block individually
        for (int i = 0; i < numBlocks && bytesRemaining > 0; i++) {
            // Calculate the block offset for this iteration
            long blockOffset = alignedOffset + (i * blockSize);

            // Calculate how much data in this block is relevant to our request
            long blockEndOffset = blockOffset + blockSize;
            long readStartOffset = Math.max(blockOffset, offset);
            long readEndOffset = Math.min(blockEndOffset, endOffset);
            int blockReadSize = (int) (readEndOffset - readStartOffset);

            if (blockReadSize <= 0) {
                continue; // Skip this block if it doesn't contain data we need
            }

            // Read the block from the delegate
            blockBuffer.clear();
            this.readRange(blockOffset, blockSize, blockBuffer);

            // Calculate position within the block for our data
            int blockPosition = (int) (readStartOffset - blockOffset);

            // Position and limit the buffer to only get the data we want
            if (blockBuffer.remaining() <= blockPosition) {
                // We've reached the end of the data
                break;
            }

            blockBuffer.position(blockBuffer.position() + blockPosition);
            int availableInBlock = blockBuffer.remaining();
            int toCopy = Math.min(blockReadSize, availableInBlock);
            blockBuffer.limit(blockBuffer.position() + toCopy);

            // Copy this block's contribution to the target
            target.put(blockBuffer);

            // Update tracking
            bytesRemaining -= toCopy;
        }

        // Calculate how many bytes were actually read
        int bytesRead = actualLength - bytesRemaining;
        return bytesRead;
    }

    protected int readRangeNoFlipOld(final long offset, final int actualLength, ByteBuffer target) throws IOException {

        // Calculate block-aligned range boundaries
        long blockMask = blockSize - 1;
        long alignedOffset = offset & ~blockMask;
        long endOffset = offset + actualLength;
        long alignedEndOffset = (endOffset + blockMask) & ~blockMask;

        // Calculate number of blocks we need to read
        int numBlocks = (int) ((alignedEndOffset - alignedOffset) / blockSize);
        if (numBlocks == 0) {
            numBlocks = 1; // At least one block
        }

        // Position tracking for partial blocks
        int bytesRemaining = actualLength;

        // Keep a small working buffer to read blocks
        final ByteBuffer blockBuffer = ByteBuffer.allocate(blockSize);

        // Read each block individually
        for (int i = 0; i < numBlocks && bytesRemaining > 0; i++) {
            // Calculate the block offset for this iteration
            long blockOffset = alignedOffset + (i * blockSize);

            // Calculate how much data in this block is relevant to our request
            long blockEndOffset = blockOffset + blockSize;
            long readStartOffset = Math.max(blockOffset, offset);
            long readEndOffset = Math.min(blockEndOffset, endOffset);
            int blockReadSize = (int) (readEndOffset - readStartOffset);

            if (blockReadSize <= 0) {
                continue; // Skip this block if it doesn't contain data we need
            }

            // Read the block from the delegate
            blockBuffer.reset();
            int blockRead = delegate.readRange(blockOffset, blockReadSize, blockBuffer);

            // Calculate position within the block for our data
            int blockPosition = (int) (readStartOffset - blockOffset);

            // Position and limit the buffer to only get the data we want
            if (blockBuffer.remaining() <= blockPosition) {
                // We've reached the end of the data
                break;
            }

            blockBuffer.position(blockBuffer.position() + blockPosition);
            int availableInBlock = blockBuffer.remaining();
            int toCopy = Math.min(blockReadSize, availableInBlock);
            blockBuffer.limit(blockBuffer.position() + toCopy);

            // Copy this block's contribution to the target
            target.put(blockBuffer);

            // Update tracking
            bytesRemaining -= toCopy;
        }

        // Calculate how many bytes were actually read
        int bytesRead = actualLength - bytesRemaining;
        return bytesRead;
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * Creates a new builder for BlockAlignedRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for BlockAlignedRangeReader.
     */
    public static class Builder {
        private RangeReader delegate;
        private int blockSize = DEFAULT_BLOCK_SIZE;

        private Builder() {}

        /**
         * Sets the delegate RangeReader to wrap with block alignment.
         *
         * @param delegate the delegate RangeReader
         * @return this builder
         */
        public Builder delegate(RangeReader delegate) {
            this.delegate = Objects.requireNonNull(delegate, "Delegate cannot be null");
            return this;
        }

        /**
         * Sets the block size for alignment.
         *
         * @param blockSize the block size (must be a positive power of 2)
         * @return this builder
         */
        public Builder blockSize(int blockSize) {
            if (blockSize <= 0) {
                throw new IllegalArgumentException("Block size must be positive: " + blockSize);
            }
            // Validate block size is a power of 2
            if ((blockSize & (blockSize - 1)) != 0) {
                throw new IllegalArgumentException("Block size must be a power of 2: " + blockSize);
            }
            this.blockSize = blockSize;
            return this;
        }

        /**
         * Builds the BlockAlignedRangeReader.
         *
         * @return a new BlockAlignedRangeReader instance
         * @throws IllegalStateException if delegate is not set
         */
        public BlockAlignedRangeReader build() {
            if (delegate == null) {
                throw new IllegalStateException("Delegate RangeReader must be set");
            }

            return new BlockAlignedRangeReader(delegate, blockSize);
        }
    }
}
