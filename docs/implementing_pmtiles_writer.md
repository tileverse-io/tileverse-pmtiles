# Implementing a PMTiles Writer

This guide provides a detailed approach to implementing a PMTiles writer in Java, focusing on the key algorithms, data structures, and optimizations needed for an efficient implementation.

## 1. Overview of the Writing Process

The process of creating a PMTiles file involves these main steps:

1. **Collect and organize tiles**
2. **Optimize and deduplicate tiles**
3. **Create directory structure**
4. **Calculate file layout**
5. **Write all sections to file**

## 2. Data Structures

These core data structures are essential for implementing a PMTiles writer:

### 2.1. Tile Representation

```java
record Tile(byte z, int x, int y, byte[] data) {
    public long tileId() {
        return ZXY.toTileId(z, x, y);
    }
}
```

### 2.2. Tile Registry

For managing the collection of tiles, including deduplication:

```java
class TileRegistry {
    // Maps tile data hash to tile metadata
    private final Map<String, TileContent> contentMap = new HashMap<>();
    
    // Maps tile ID to content hash
    private final Map<Long, String> tileMap = new HashMap<>();
    
    // Add a tile to the registry
    public void addTile(Tile tile) {
        String hash = hashTileData(tile.data());
        contentMap.putIfAbsent(hash, new TileContent(hash, tile.data()));
        tileMap.put(tile.tileId(), hash);
    }
    
    // Get all unique tile contents
    public Collection<TileContent> getUniqueContents() {
        return contentMap.values();
    }
    
    // Get directory entries for all tiles
    public List<PMTilesEntry> createDirectoryEntries() {
        // Implementation details in section 3.3
    }
    
    private String hashTileData(byte[] data) {
        // Use a secure hash function like SHA-256
        // Return as hex string
    }
    
    record TileContent(String hash, byte[] data) {}
}
```

### 2.3. Directory Entry Builder

For efficient creation of directory entries with run-length encoding:

```java
class DirectoryEntryBuilder {
    private final List<PMTilesEntry> entries = new ArrayList<>();
    private final Map<String, Long> contentOffsets = new HashMap<>();
    
    // Add tile ID with content reference
    public void addTile(long tileId, String contentHash) {
        // Logic for creating and optimizing entries
        // Details in section 3.3
    }
    
    // Assign offsets to unique contents
    public void assignOffsets(Collection<TileContent> contents) {
        long offset = 0;
        for (TileContent content : contents) {
            contentOffsets.put(content.hash(), offset);
            offset += content.data().length;
        }
    }
    
    // Get final list of optimized entries
    public List<PMTilesEntry> getEntries() {
        return entries;
    }
}
```

## 3. Core Algorithms

### 3.1. Tile ID Calculation

Implementing the Hilbert curve for optimal spatial locality:

```java
public static long toTileId(byte z, int x, int y) {
    // First, accumulate tiles from previous zoom levels
    long acc = 0;
    for (byte t_z = 0; t_z < z; t_z++) {
        long tilesPerZoom = 1L << t_z;
        acc += tilesPerZoom * tilesPerZoom;
    }
    
    // Convert x,y to position on Hilbert curve
    long n = 1L << z;
    long tx = x;
    long ty = y;
    long d = 0;
    
    for (long s = n / 2; s > 0; s /= 2) {
        long rx = (tx & s) > 0 ? 1 : 0;
        long ry = (ty & s) > 0 ? 1 : 0;
        d += s * s * ((3 * rx) ^ ry);
        rotate(s, rx, ry, tx, ty);
    }
    
    return acc + d;
}

private static void rotate(long s, long rx, long ry, long tx, long ty) {
    if (ry == 0) {
        if (rx == 1) {
            tx = s - 1 - tx;
            ty = s - 1 - ty;
        }
        // Swap tx and ty
        long t = tx;
        tx = ty;
        ty = t;
    }
}
```

### 3.2. Tile Deduplication

Efficiently identifying and handling duplicate tiles:

```java
public void processTiles(Collection<Tile> tiles) {
    // First pass: collect and deduplicate
    TileRegistry registry = new TileRegistry();
    for (Tile tile : tiles) {
        registry.addTile(tile);
    }
    
    // Second pass: organize by tile ID and apply run-length encoding
    DirectoryEntryBuilder entryBuilder = new DirectoryEntryBuilder();
    Map<Long, String> tileMap = registry.getTileMap();
    List<Long> sortedTileIds = new ArrayList<>(tileMap.keySet());
    Collections.sort(sortedTileIds);
    
    for (Long tileId : sortedTileIds) {
        entryBuilder.addTile(tileId, tileMap.get(tileId));
    }
    
    // Assign offsets to unique contents
    entryBuilder.assignOffsets(registry.getUniqueContents());
    
    List<PMTilesEntry> entries = entryBuilder.getEntries();
    // Proceed to directory creation...
}
```

### 3.3. Run-Length Encoding

Optimizing directory entries with run-length encoding:

```java
public void addTile(long tileId, String contentHash) {
    // Check if we can extend an existing run
    if (!entries.isEmpty()) {
        PMTilesEntry lastEntry = entries.get(entries.size() - 1);
        long lastContentOffset = contentOffsets.get(lastContentHash);
        long newContentOffset = contentOffsets.get(contentHash);
        
        if (lastEntry.tileId() + lastEntry.runLength() == tileId && 
            lastContentOffset == newContentOffset) {
            // Extend the run
            entries.set(entries.size() - 1, 
                new PMTilesEntry(
                    lastEntry.tileId(), 
                    lastEntry.offset(), 
                    lastEntry.length(), 
                    lastEntry.runLength() + 1
                )
            );
            return;
        }
    }
    
    // Start a new run
    long offset = contentOffsets.get(contentHash);
    int length = contentLengths.get(contentHash);
    entries.add(new PMTilesEntry(tileId, offset, length, 1));
    lastContentHash = contentHash;
}
```

### 3.4. Directory Structure Creation

Building optimized one or two-level directory structure:

```java
public DirectoryResult buildDirectoryStructure(List<PMTilesEntry> entries, byte compressionType) 
        throws IOException {
    // Try with just a root directory first
    byte[] rootDir = serializeDirectory(entries);
    byte[] compressedRootDir = compress(rootDir, compressionType);
    
    // If the root directory is small enough, we're done
    if (compressedRootDir.length <= MAX_ROOT_DIR_SIZE) {
        return new DirectoryResult(compressedRootDir, new byte[0], 0);
    }
    
    // Otherwise, build a two-level structure
    return buildTwoLevelDirectory(entries, compressionType);
}

private DirectoryResult buildTwoLevelDirectory(List<PMTilesEntry> entries, byte compressionType) 
        throws IOException {
    // Determine leaf size
    int leafSize = calculateOptimalLeafSize(entries, compressionType);
    
    List<PMTilesEntry> rootEntries = new ArrayList<>();
    ByteArrayOutputStream leafStream = new ByteArrayOutputStream();
    int numLeaves = 0;
    
    // Create leaf directories
    for (int i = 0; i < entries.size(); i += leafSize) {
        numLeaves++;
        int end = Math.min(i + leafSize, entries.size());
        List<PMTilesEntry> leafEntries = entries.subList(i, end);
        
        byte[] leafDir = serializeDirectory(leafEntries);
        byte[] compressedLeafDir = compress(leafDir, compressionType);
        
        // Create root entry pointing to this leaf
        rootEntries.add(PMTilesEntry.leaf(
            leafEntries.get(0).tileId(),
            leafStream.size(),
            compressedLeafDir.length
        ));
        
        leafStream.write(compressedLeafDir);
    }
    
    // Create root directory
    byte[] rootDir = serializeDirectory(rootEntries);
    byte[] compressedRootDir = compress(rootDir, compressionType);
    
    return new DirectoryResult(compressedRootDir, leafStream.toByteArray(), numLeaves);
}
```

### 3.5. Directory Serialization

Efficiently serializing directory entries:

```java
public byte[] serializeDirectory(List<PMTilesEntry> entries) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    // Write number of entries
    writeVarint(out, entries.size());
    
    // Write tile IDs (delta encoded)
    long lastId = 0;
    for (PMTilesEntry entry : entries) {
        writeVarint(out, entry.tileId() - lastId);
        lastId = entry.tileId();
    }
    
    // Write run lengths
    for (PMTilesEntry entry : entries) {
        writeVarint(out, entry.runLength());
    }
    
    // Write lengths
    for (PMTilesEntry entry : entries) {
        writeVarint(out, entry.length());
    }
    
    // Write offsets (with optimization for consecutive entries)
    for (int i = 0; i < entries.size(); i++) {
        if (i > 0 && entries.get(i).offset() == entries.get(i - 1).offset() + entries.get(i - 1).length()) {
            writeVarint(out, 0);
        } else {
            writeVarint(out, entries.get(i).offset() + 1);
        }
    }
    
    return out.toByteArray();
}

private void writeVarint(OutputStream out, long value) throws IOException {
    while (value >= 0x80L) {
        out.write((int) ((value & 0x7FL) | 0x80L));
        value >>>= 7;
    }
    out.write((int) value);
}
```

## 4. File Assembly

### 4.1. Layout Calculation

Determining the layout of the PMTiles file:

```java
private FileLayout calculateLayout(
        DirectoryResult directoryResult,
        byte[] metadata,
        Collection<TileContent> tileContents) {
    
    long rootDirOffset = 127;  // Start after header
    long rootDirBytes = directoryResult.rootDirectory().length;
    
    long metadataOffset = rootDirOffset + rootDirBytes;
    long metadataBytes = metadata.length;
    
    long leafDirsOffset = metadataOffset + metadataBytes;
    long leafDirsBytes = directoryResult.leafDirectories().length;
    
    long tileDataOffset = leafDirsOffset + leafDirsBytes;
    long tileDataBytes = calculateTotalTileSize(tileContents);
    
    return new FileLayout(
        rootDirOffset, rootDirBytes,
        metadataOffset, metadataBytes,
        leafDirsOffset, leafDirsBytes,
        tileDataOffset, tileDataBytes
    );
}

private long calculateTotalTileSize(Collection<TileContent> tileContents) {
    return tileContents.stream()
        .mapToLong(content -> content.data().length)
        .sum();
}

record FileLayout(
    long rootDirOffset, long rootDirBytes,
    long metadataOffset, long metadataBytes,
    long leafDirsOffset, long leafDirsBytes,
    long tileDataOffset, long tileDataBytes
) {}
```

### 4.2. Header Creation

Building the PMTiles header:

```java
private PMTilesHeader createHeader(
        FileLayout layout,
        DirectoryResult directoryResult,
        PMTilesMetadata metadata,
        int uniqueTileCount,
        int entryCount,
        boolean clustered) {
    
    return PMTilesHeader.builder()
        .rootDirOffset(layout.rootDirOffset())
        .rootDirBytes(layout.rootDirBytes())
        .jsonMetadataOffset(layout.metadataOffset())
        .jsonMetadataBytes(layout.metadataBytes())
        .leafDirsOffset(layout.leafDirsOffset())
        .leafDirsBytes(layout.leafDirsBytes())
        .tileDataOffset(layout.tileDataOffset())
        .tileDataBytes(layout.tileDataBytes())
        .addressedTilesCount(metadata.addressedTilesCount())
        .tileEntriesCount(entryCount)
        .tileContentsCount(uniqueTileCount)
        .clustered(clustered)
        .internalCompression(metadata.internalCompression())
        .tileCompression(metadata.tileCompression())
        .tileType(metadata.tileType())
        .minZoom(metadata.minZoom())
        .maxZoom(metadata.maxZoom())
        .minLon(metadata.minLon())
        .minLat(metadata.minLat())
        .maxLon(metadata.maxLon())
        .maxLat(metadata.maxLat())
        .centerZoom(metadata.centerZoom())
        .centerLon(metadata.centerLon())
        .centerLat(metadata.centerLat())
        .build();
}
```

### 4.3. File Writing

Writing all components to the file:

```java
public void write(Path outputPath) throws IOException {
    // Prepare all components
    TileRegistry registry = processTiles();
    List<PMTilesEntry> entries = registry.createDirectoryEntries();
    
    byte[] metadata = serializeMetadata();
    byte[] compressedMetadata = compress(metadata, COMPRESSION_GZIP);
    
    DirectoryResult directoryResult = buildDirectoryStructure(entries, COMPRESSION_GZIP);
    
    Collection<TileContent> tileContents = registry.getUniqueContents();
    
    FileLayout layout = calculateLayout(directoryResult, compressedMetadata, tileContents);
    
    PMTilesHeader header = createHeader(
        layout,
        directoryResult,
        metadata,
        tileContents.size(),
        entries.size(),
        true // For clustered organization
    );
    
    // Write file
    try (FileChannel channel = FileChannel.open(outputPath, 
            StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        
        // Write header
        byte[] headerBytes = header.serialize();
        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);
        channel.write(headerBuffer);
        
        // Write root directory
        ByteBuffer rootDirBuffer = ByteBuffer.wrap(directoryResult.rootDirectory());
        channel.write(rootDirBuffer);
        
        // Write metadata
        ByteBuffer metadataBuffer = ByteBuffer.wrap(compressedMetadata);
        channel.write(metadataBuffer);
        
        // Write leaf directories
        ByteBuffer leafDirsBuffer = ByteBuffer.wrap(directoryResult.leafDirectories());
        channel.write(leafDirsBuffer);
        
        // Write tile data
        writeTileData(channel, tileContents);
    }
}

private void writeTileData(FileChannel channel, Collection<TileContent> tileContents) 
        throws IOException {
    // Ensure tiles are written in the correct order
    List<TileContent> sortedContents = sortTileContentsByOffset(tileContents);
    
    for (TileContent content : sortedContents) {
        ByteBuffer buffer = ByteBuffer.wrap(content.data());
        channel.write(buffer);
    }
}
```

## 5. Optimizations

### 5.1. Memory Management

Handling large tilesets efficiently:

```java
public class StreamingTileProcessor {
    private final Path tempDir;
    private final TileSource tileSource;
    private Map<String, Path> contentFiles = new HashMap<>();
    
    // Process tiles in batches to avoid excessive memory usage
    public void processTiles() throws IOException {
        try (TileIterator tiles = tileSource.iterator()) {
            while (tiles.hasNext()) {
                Tile tile = tiles.next();
                processTile(tile);
            }
        }
    }
    
    private void processTile(Tile tile) throws IOException {
        String hash = hashTileData(tile.data());
        
        // Store unique content on disk if not already stored
        if (!contentFiles.containsKey(hash)) {
            Path tempFile = Files.createTempFile(tempDir, "tile-", ".bin");
            Files.write(tempFile, tile.data());
            contentFiles.put(hash, tempFile);
        }
        
        // Record the tile ID and content reference
        recordTileReference(tile.tileId(), hash);
    }
    
    // Other methods for managing the tile references and assembling the final file
}
```

### 5.2. Parallel Processing

Using parallelism for performance:

```java
public void generateTiles(Collection<Feature> features) {
    // Split features into batches for parallel processing
    List<List<Feature>> batches = splitIntoBatches(features);
    
    // Process batches in parallel
    List<Collection<Tile>> results = batches.parallelStream()
        .map(this::processBatch)
        .collect(Collectors.toList());
    
    // Merge results
    List<Tile> allTiles = results.stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
    
    // Continue with tile optimization and file creation
}

private Collection<Tile> processBatch(List<Feature> features) {
    List<Tile> tiles = new ArrayList<>();
    for (Feature feature : features) {
        // Generate tiles for this feature
        Collection<Tile> featureTiles = generateTilesForFeature(feature);
        tiles.addAll(featureTiles);
    }
    return tiles;
}
```

### 5.3. Buffered I/O

Efficient file writing:

```java
private void writeTileData(FileChannel channel, Collection<TileContent> tileContents) 
        throws IOException {
    // Use a buffer for efficient writing
    ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
    
    for (TileContent content : tileContents) {
        byte[] data = content.data();
        int position = 0;
        
        while (position < data.length) {
            int remaining = data.length - position;
            int chunkSize = Math.min(remaining, buffer.capacity());
            
            buffer.clear();
            buffer.put(data, position, chunkSize);
            buffer.flip();
            
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            
            position += chunkSize;
        }
    }
}
```

### 5.4. Progressive Feedback

Providing progress updates:

```java
public void write(Path outputPath, ProgressListener listener) throws IOException {
    // Calculate total work
    long totalWork = 
        estimateDirectoryWork() + 
        estimateMetadataWork() + 
        estimateTileDataWork();
    
    long completedWork = 0;
    
    // Process directory
    DirectoryResult directoryResult = buildDirectoryStructure(entries, COMPRESSION_GZIP);
    completedWork += estimateDirectoryWork();
    listener.onProgress(completedWork / (double) totalWork);
    
    // Process metadata
    byte[] metadata = serializeMetadata();
    byte[] compressedMetadata = compress(metadata, COMPRESSION_GZIP);
    completedWork += estimateMetadataWork();
    listener.onProgress(completedWork / (double) totalWork);
    
    // Write file components with progress updates
    writeTileData(channel, tileContents, listener, 
        completedWork, totalWork - completedWork);
}

private void writeTileData(FileChannel channel, Collection<TileContent> tileContents,
                          ProgressListener listener, long baseProgress, long workAmount) 
        throws IOException {
    
    long totalBytes = tileContents.stream()
        .mapToLong(content -> content.data().length)
        .sum();
    
    long writtenBytes = 0;
    
    for (TileContent content : tileContents) {
        ByteBuffer buffer = ByteBuffer.wrap(content.data());
        channel.write(buffer);
        
        writtenBytes += content.data().length;
        double progress = baseProgress + (writtenBytes / (double) totalBytes) * workAmount;
        listener.onProgress(progress / totalWork);
    }
}
```

## 6. Error Handling

### 6.1. Validation

Validating inputs to ensure correct PMTiles generation:

```java
private void validateTiles(Collection<Tile> tiles) {
    if (tiles.isEmpty()) {
        throw new IllegalArgumentException("No tiles provided");
    }
    
    // Validate zoom level range
    byte minZoom = Byte.MAX_VALUE;
    byte maxZoom = Byte.MIN_VALUE;
    
    for (Tile tile : tiles) {
        if (tile.z() < 0 || tile.z() > 32) {
            throw new IllegalArgumentException(
                "Invalid zoom level: " + tile.z()
            );
        }
        
        minZoom = (byte) Math.min(minZoom, tile.z());
        maxZoom = (byte) Math.max(maxZoom, tile.z());
        
        int maxCoord = (1 << tile.z()) - 1;
        if (tile.x() < 0 || tile.x() > maxCoord || tile.y() < 0 || tile.y() > maxCoord) {
            throw new IllegalArgumentException(
                "Invalid tile coordinates: " + tile.z() + "/" + tile.x() + "/" + tile.y()
            );
        }
        
        if (tile.data() == null || tile.data().length == 0) {
            throw new IllegalArgumentException(
                "Empty tile data for: " + tile.z() + "/" + tile.x() + "/" + tile.y()
            );
        }
    }
    
    if (maxZoom - minZoom > 24) {
        throw new IllegalArgumentException("Zoom level range too large: " + 
            (maxZoom - minZoom) + " levels");
    }
}
```

### 6.2. Resource Management

Proper handling of resources and error conditions:

```java
public void write(Path outputPath) throws IOException {
    // Create temporary files
    Path tempTileData = Files.createTempFile("pmtiles-", "-data");
    Path tempDirectory = Files.createTempFile("pmtiles-", "-dir");
    
    try {
        // Process tiles and write to temporary files
        processTilesToTempFile(tempTileData);
        createDirectoryInTempFile(tempDirectory);
        
        // Assemble final file
        assembleFinalFile(outputPath, tempTileData, tempDirectory);
    } catch (Exception e) {
        // Clean up on error
        try {
            Files.deleteIfExists(tempTileData);
            Files.deleteIfExists(tempDirectory);
        } catch (IOException cleanupError) {
            // Log cleanup error but don't mask original exception
            e.addSuppressed(cleanupError);
        }
        throw e;
    } finally {
        // Always clean up temps
        try {
            Files.deleteIfExists(tempTileData);
            Files.deleteIfExists(tempDirectory);
        } catch (IOException ignored) {
            // Best effort cleanup
        }
    }
}
```

## 7. Testing Strategies

### 7.1. Unit Testing

```java
@Test
public void testHilbertCurveEncoding() {
    // Test specific known values
    assertEquals(0, ZXY.toTileId((byte) 0, 0, 0));
    assertEquals(1, ZXY.toTileId((byte) 1, 0, 0));
    assertEquals(2, ZXY.toTileId((byte) 1, 1, 0));
    assertEquals(3, ZXY.toTileId((byte) 1, 1, 1));
    assertEquals(4, ZXY.toTileId((byte) 1, 0, 1));
    
    // Test round-trip conversion
    for (int z = 0; z < 5; z++) {
        int maxCoord = (1 << z) - 1;
        for (int x = 0; x <= maxCoord; x++) {
            for (int y = 0; y <= maxCoord; y++) {
                long tileId = ZXY.toTileId((byte) z, x, y);
                ZXY coords = ZXY.fromTileId(tileId);
                assertEquals(z, coords.z());
                assertEquals(x, coords.x());
                assertEquals(y, coords.y());
            }
        }
    }
}

@Test
public void testDirectorySerialization() throws IOException {
    List<PMTilesEntry> entries = List.of(
        new PMTilesEntry(1, 0, 100, 1),
        new PMTilesEntry(2, 100, 200, 2),
        new PMTilesEntry(5, 300, 150, 1)
    );
    
    byte[] serialized = DirectoryUtil.serializeDirectory(entries);
    List<PMTilesEntry> deserialized = DirectoryUtil.deserializeDirectory(serialized);
    
    assertEquals(entries.size(), deserialized.size());
    for (int i = 0; i < entries.size(); i++) {
        assertEquals(entries.get(i).tileId(), deserialized.get(i).tileId());
        assertEquals(entries.get(i).offset(), deserialized.get(i).offset());
        assertEquals(entries.get(i).length(), deserialized.get(i).length());
        assertEquals(entries.get(i).runLength(), deserialized.get(i).runLength());
    }
}
```

### 7.2. Integration Testing

```java
@Test
public void testEndToEndWriteAndRead() throws IOException {
    // Create test tiles
    List<Tile> testTiles = createTestTiles();
    
    // Create PMTiles writer and generate file
    Path outputPath = Files.createTempFile("test-", ".pmtiles");
    try {
        PMTilesWriter writer = new PMTilesWriter()
            .minZoom((byte) 0)
            .maxZoom((byte) 2)
            .tileType(PMTilesHeader.TILETYPE_MVT)
            .compression(PMTilesHeader.COMPRESSION_GZIP);
            
        for (Tile tile : testTiles) {
            writer.addTile(tile);
        }
        
        writer.write(outputPath);
        
        // Verify with reader
        try (PMTilesReader reader = new PMTilesReader(outputPath)) {
            PMTilesHeader header = reader.getHeader();
            assertEquals(0, header.minZoom());
            assertEquals(2, header.maxZoom());
            assertEquals(PMTilesHeader.TILETYPE_MVT, header.tileType());
            
            // Check all tiles can be read
            for (Tile tile : testTiles) {
                Optional<byte[]> tileData = reader.getTile(tile.z(), tile.x(), tile.y());
                assertTrue(tileData.isPresent());
                
                // For compressed tiles, compare after decompression
                byte[] decompressed = CompressionUtil.decompress(
                    tileData.get(), 
                    PMTilesHeader.COMPRESSION_GZIP
                );
                assertArrayEquals(tile.data(), decompressed);
            }
        }
    } finally {
        Files.deleteIfExists(outputPath);
    }
}
```

### 7.3. Performance Testing

```java
@Test
public void testLargeFilePerformance() throws IOException {
    // Generate a large number of tiles
    int tileCount = 100_000;
    List<Tile> tiles = generateRandomTiles(tileCount);
    
    // Measure time for processing
    long startTime = System.currentTimeMillis();
    
    Path outputPath = Files.createTempFile("perf-", ".pmtiles");
    try {
        PMTilesWriter writer = new PMTilesWriter();
        for (Tile tile : tiles) {
            writer.addTile(tile);
        }
        writer.write(outputPath);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Processed " + tileCount + " tiles in " + duration + "ms");
        System.out.println("Average: " + (duration / (double) tileCount) + "ms per tile");
        
        // Verify file size is reasonable
        long fileSize = Files.size(outputPath);
        System.out.println("File size: " + fileSize + " bytes");
        System.out.println("Bytes per tile: " + (fileSize / (double) tileCount));
        
        // Performance assertions
        assertTrue(duration < 30_000, "Processing took too long: " + duration + "ms");
    } finally {
        Files.deleteIfExists(outputPath);
    }
}
```

## 8. Advanced Features

### 8.1. Custom Compression

Supporting multiple compression types:

```java
public enum CompressionType {
    NONE(PMTilesHeader.COMPRESSION_NONE),
    GZIP(PMTilesHeader.COMPRESSION_GZIP),
    BROTLI(PMTilesHeader.COMPRESSION_BROTLI),
    ZSTD(PMTilesHeader.COMPRESSION_ZSTD);
    
    private final byte value;
    
    CompressionType(byte value) {
        this.value = value;
    }
    
    public byte getValue() {
        return value;
    }
}

public interface Compressor {
    byte[] compress(byte[] data) throws IOException;
    byte[] decompress(byte[] data) throws IOException;
    byte getType();
}

public class GzipCompressor implements Compressor {
    @Override
    public byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }
    
    @Override
    public byte[] decompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }
    
    @Override
    public byte getType() {
        return PMTilesHeader.COMPRESSION_GZIP;
    }
}

// Factory for getting appropriate compressor
public class CompressionFactory {
    public static Compressor getCompressor(CompressionType type) {
        return switch (type) {
            case NONE -> new NoopCompressor();
            case GZIP -> new GzipCompressor();
            case BROTLI -> new BrotliCompressor();
            case ZSTD -> new ZstdCompressor();
        };
    }
}
```

### 8.2. Metadata Customization

Creating rich, customizable metadata:

```java
public record VectorLayer(
    String id,
    String description,
    byte minZoom,
    byte maxZoom,
    Map<String, String> fields
) {
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        
        if (description != null && !description.isEmpty()) {
            obj.addProperty("description", description);
        }
        
        obj.addProperty("minzoom", minZoom);
        obj.addProperty("maxzoom", maxZoom);
        
        if (fields != null && !fields.isEmpty()) {
            JsonObject fieldsObj = new JsonObject();
            fields.forEach(fieldsObj::addProperty);
            obj.add("fields", fieldsObj);
        }
        
        return obj;
    }
}

public class PMTilesMetadata {
    private String name;
    private String format = "pbf";
    private String type = "overlay";
    private String description;
    private String version = "1.0.0";
    private String attribution;
    private List<String> strategies = new ArrayList<>();
    private JsonObject tippecanoeDecisions = new JsonObject();
    private String generator = "Tileverse";
    private String generatorOptions;
    private List<VectorLayer> vectorLayers = new ArrayList<>();
    private JsonObject tileStats;
    private byte minZoom = 0;
    private byte maxZoom = 14;
    private double minLon = -180;
    private double minLat = -85.05113;
    private double maxLon = 180;
    private double maxLat = 85.05113;
    private byte centerZoom = 0;
    private double centerLon = 0;
    private double centerLat = 0;
    
    // Builder methods
    public PMTilesMetadata name(String name) {
        this.name = name;
        return this;
    }
    
    // Additional builder methods
    
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        
        if (name != null) obj.addProperty("name", name);
        obj.addProperty("format", format);
        obj.addProperty("type", type);
        if (description != null) obj.addProperty("description", description);
        obj.addProperty("version", version);
        if (attribution != null) obj.addProperty("attribution", attribution);
        
        if (!strategies.isEmpty()) {
            JsonArray strategiesArray = new JsonArray();
            strategies.forEach(strategiesArray::add);
            obj.add("strategies", strategiesArray);
        }
        
        if (tippecanoeDecisions.size() > 0) {
            obj.add("tippecanoe_decisions", tippecanoeDecisions);
        }
        
        obj.addProperty("generator", generator);
        if (generatorOptions != null) obj.addProperty("generator_options", generatorOptions);
        
        String bounds = minLon + "," + minLat + "," + maxLon + "," + maxLat;
        obj.addProperty("antimeridian_adjusted_bounds", bounds);
        
        if (!vectorLayers.isEmpty()) {
            JsonArray layersArray = new JsonArray();
            for (VectorLayer layer : vectorLayers) {
                layersArray.add(layer.toJson());
            }
            obj.add("vector_layers", layersArray);
        }
        
        if (tileStats != null) {
            obj.add("tilestats", tileStats);
        }
        
        return obj;
    }
    
    public String toJsonString() {
        return toJson().toString();
    }
}
```

## 9. Best Practices

### 9.1. Memory Efficiency

- Process tiles in batches to avoid excessive memory usage
- Use temporary files for large datasets
- Implement streaming interfaces for data sources
- Consider memory-mapped files for large operations

### 9.2. Performance

- Use parallel processing for tile generation and processing
- Optimize the two-level directory structure for large tilesets
- Implement efficient hashing and deduplication
- Use buffered I/O for file operations

### 9.3. API Design

- Provide a clean builder pattern for configuration
- Support progress reporting and cancellation
- Separate concerns between tile generation, optimization, and file writing
- Allow customization of key parameters (compression, etc.)

### 9.4. Error Handling

- Validate inputs thoroughly
- Provide clear error messages for common issues
- Clean up temporary resources in finally blocks
- Implement proper exception hierarchies

## 10. Conclusion

Implementing a PMTiles writer in Java involves several complex components, but with careful design and attention to algorithms, it can be both efficient and flexible. By focusing on key optimizations like spatial ordering, run-length encoding, and efficient directory structures, the implementation can handle large datasets while maintaining good performance.

The modular approach outlined in this guide allows for extension and customization while maintaining compatibility with the PMTiles format specification. By following these implementation guidelines, you can create a robust writer that integrates well with existing systems and performs efficiently even with large datasets.