package io.tileverse.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

/**
 * Utility class for compressing and decompressing data using various compression algorithms.
 */
public final class CompressionUtil {

    private CompressionUtil() {
        // Prevent instantiation
    }

    /**
     * Compresses data using the specified compression type.
     *
     * @param data the data to compress
     * @param compressionType the compression type to use
     * @return the compressed data
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedCompressionException if the compression type is not supported
     */
    public static byte[] compress(byte[] data, byte compressionType)
            throws IOException, UnsupportedCompressionException {
        if (compressionType == PMTilesHeader.COMPRESSION_NONE) {
            return data;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (OutputStream compressor = createCompressor(outputStream, compressionType)) {
            compressor.write(data);
            compressor.flush();
        }

        return outputStream.toByteArray();
    }

    /**
     * Decompresses data using the specified compression type.
     *
     * @param data the data to decompress
     * @param compressionType the compression type used
     * @return the decompressed data
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedCompressionException if the compression type is not supported
     */
    public static byte[] decompress(byte[] data, byte compressionType)
            throws IOException, UnsupportedCompressionException {
        if (compressionType == PMTilesHeader.COMPRESSION_NONE) {
            return data;
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (CompressorInputStream decompressor = createDecompressor(inputStream, compressionType)) {
            IOUtils.copy(decompressor, outputStream);
        } catch (CompressorException e) {
            throw new IOException("Failed to create decompressor", e);
        }

        return outputStream.toByteArray();
    }

    /**
     * Creates a compressor for the specified compression type.
     *
     * @param outputStream the output stream to write compressed data to
     * @param compressionType the compression type to use
     * @return a compressor output stream
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedCompressionException if the compression type is not supported
     */
    private static OutputStream createCompressor(OutputStream outputStream, byte compressionType)
            throws IOException, UnsupportedCompressionException {
        switch (compressionType) {
            case PMTilesHeader.COMPRESSION_GZIP:
                return new GzipCompressorOutputStream(outputStream);
            case PMTilesHeader.COMPRESSION_NONE:
                return outputStream;
            case PMTilesHeader.COMPRESSION_BROTLI:
            // Brotli implementation would go here
            case PMTilesHeader.COMPRESSION_ZSTD:
            // ZSTD implementation would go here
            default:
                throw new UnsupportedCompressionException("Compression type not supported: " + compressionType);
        }
    }

    /**
     * Creates a decompressor for the specified compression type.
     *
     * @param inputStream the input stream containing compressed data
     * @param compressionType the compression type used
     * @return a decompressor input stream
     * @throws IOException if an I/O error occurs
     * @throws CompressorException if the compressor creation fails
     * @throws UnsupportedCompressionException if the compression type is not supported
     */
    private static CompressorInputStream createDecompressor(InputStream inputStream, byte compressionType)
            throws IOException, CompressorException, UnsupportedCompressionException {
        switch (compressionType) {
            case PMTilesHeader.COMPRESSION_GZIP:
                return new GzipCompressorInputStream(inputStream, true);
            case PMTilesHeader.COMPRESSION_NONE:
                throw new IllegalArgumentException("Cannot create decompressor for COMPRESSION_NONE");
            case PMTilesHeader.COMPRESSION_BROTLI:
            // Brotli implementation would go here
            case PMTilesHeader.COMPRESSION_ZSTD:
            // ZSTD implementation would go here
            default:
                throw new UnsupportedCompressionException("Compression type not supported: " + compressionType);
        }
    }

    /**
     * Exception thrown when an unsupported compression type is used.
     */
    public static class UnsupportedCompressionException extends Exception {
        public UnsupportedCompressionException(String message) {
            super(message);
        }

        public UnsupportedCompressionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
