# Tileverse PMTiles Architecture and Design

This document describes the architecture and design principles of the Tileverse PMTiles library, focusing on its components, interfaces, and how they interact to provide a comprehensive Java implementation for reading and writing PMTiles files.

## 1. Architectural Overview

Tileverse follows a modular, layered architecture that separates core functionality from high-level operations:

![Tileverse Architecture](architecture_diagram.svg)

### 1.1. Module Structure

The library is focused on PMTiles functionality with these main modules:

1. **tileverse-pmtiles-reader**
   - Core PMTiles format implementation
   - Reading and writing PMTiles files
   - Integration with tileverse-rangereader for multi-source access

2. **tileverse-mvt** (planned)
   - Vector tile encoding/decoding
   - Mapbox Vector Tiles support
   - Depends on pmtiles-reader module

3. **tileverse-cli** (planned)
   - Command-line interface
   - PMTiles operations and utilities
   - Integration with reader module

### 1.2. Dependency Hierarchy

The dependencies between modules flow in one direction to prevent circular dependencies:

```
tileverse-cli
    └── tileverse-pmtiles-reader
        └── tileverse-rangereader (external dependency)
            ├── tileverse-rangereader-core
            ├── tileverse-rangereader-s3
            ├── tileverse-rangereader-azure
            └── tileverse-rangereader-gcs

tileverse-mvt
    └── tileverse-pmtiles-reader
```

## 2. Core Design Principles

### 2.1. Separation of Concerns

Each module has a distinct responsibility:

- **PMTiles Reader**: PMTiles format reading and writing
- **MVT**: Vector tile encoding/decoding (planned)
- **CLI**: Command-line utilities (planned)

The library depends on **Tileverse Range Reader** for efficient data access from multiple sources including local files, HTTP servers, and cloud storage.

### 2.2. Immutability and Thread Safety

- Immutable objects for key data structures (e.g., `PMTilesHeader`, `ZXY`)
- Thread-safe operations for parallel processing
- Pure functions for key algorithms
- State encapsulation in processing components

### 2.3. Extensibility

- Interface-based design for key components
- Plugin architecture for custom processors
- Clear extension points for adding new functionality
- Separation of interface from implementation

### 2.4. Progressive Disclosure

- Simple interfaces for common operations
- Advanced options available but not required
- Builder patterns for complex configuration
- Sensible defaults for most parameters

## 3. Key Abstractions

### 3.1. PMTiles Reader Module

#### 3.1.1. PMTiles Core Components

```java
// Tile coordinates
public record ZXY(byte z, int x, int y) { ... }

// PMTiles file header
public record PMTilesHeader( ... ) { ... }

// Directory entry
public record PMTilesEntry(long tileId, long offset, int length, int runLength) { ... }

// Interfaces for reading and writing
public interface PMTilesReader extends Closeable { ... }
public interface PMTilesWriter extends Closeable { ... }
```

#### 3.1.2. Directory Structure

```java
// Directory utility
public class DirectoryUtil {
    public static byte[] serializeDirectory(List<PMTilesEntry> entries) { ... }
    public static List<PMTilesEntry> deserializeDirectory(byte[] data) { ... }
    public static DirectoryResult buildRootLeaves(...) { ... }
}

// Result of directory creation
public record DirectoryResult(byte[] rootDirectory, byte[] leafDirectories, int numLeaves) { ... }
```

#### 3.1.3. Utilities

```java
// Compression utility
public class CompressionUtil {
    public static byte[] compress(byte[] data, byte compressionType) { ... }
    public static byte[] decompress(byte[] data, byte compressionType) { ... }
}

// IO utilities
public class IOUtil {
    public static void writeVarint(OutputStream out, long value) { ... }
    public static long readVarint(InputStream in) { ... }
}
```

### 3.2. MVT Module (Planned)

#### 3.2.1. Vector Tile Structure

```java
// Vector tile
public class VectorTile {
    private List<Layer> layers;
    // Methods for adding/accessing layers
    
    public static VectorTile decode(byte[] data) { ... }
    public byte[] encode() { ... }
}

// Layer
public class Layer {
    private String name;
    private List<Feature> features;
    private int extent = 4096;
    // Methods for adding/accessing features
}

// Feature
public class Feature {
    private Geometry geometry;
    private Map<String, Value> attributes;
    private long id;
    // Methods for accessing/modifying
}
```

### 3.3. Integration with Tileverse Range Reader

The library integrates with [Tileverse Range Reader](https://github.com/tileverse-io/tileverse-rangereader) for multi-source data access:

```java
// Multi-source PMTiles reading
RangeReader rangeReader = S3RangeReader.builder()
    .uri(URI.create("s3://bucket/tiles.pmtiles"))
    .build();

// Apply caching decorators
RangeReader cachedReader = CachingRangeReader.builder(
    BlockAlignedRangeReader.builder(rangeReader)
        .blockSize(64 * 1024)
        .build())
    .maximumSize(1000)
    .build();

try (PMTilesReader reader = new PMTilesReader(cachedReader)) {
    // Efficient cloud-optimized access
}
```

## 4. Interaction Patterns

### 4.1. Reading PMTiles

```java
// Local file reading
RangeReader rangeReader = FileRangeReader.builder()
    .path(Path.of("map.pmtiles"))
    .build();

try (PMTilesReader reader = new PMTilesReader(rangeReader)) {
    PMTilesHeader header = reader.getHeader();
    byte[] metadata = reader.getMetadata();
    
    // Get a specific tile
    Optional<byte[]> tileData = reader.getTile(10, 885, 412);
    
    // Process tiles
    reader.streamTiles(12, tile -> {
        // Process each tile at zoom level 12
    });
}
```

### 4.2. Cloud Storage Access

```java
// S3 with optimal caching
RangeReader s3Reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/tiles.pmtiles"))
    .region(Region.US_WEST_2)
    .build();

RangeReader optimizedReader = CachingRangeReader.builder(
    BlockAlignedRangeReader.builder(s3Reader)
        .blockSize(64 * 1024)
        .build())
    .maximumSize(1000)
    .build();

try (PMTilesReader reader = new PMTilesReader(optimizedReader)) {
    // Efficient cloud access
}
```

### 4.3. Writing PMTiles

```java
// Create PMTiles file
try (PMTilesWriter writer = new PMTilesWriter(outputPath)) {
    writer.setMetadata(metadataJson.getBytes(StandardCharsets.UTF_8));
    
    // Add tiles
    writer.addTile(0, 0, 0, tileData);
    writer.addTile(1, 0, 0, tileData);
    writer.addTile(1, 0, 1, tileData);
    
    writer.complete(); // Finalizes directory structure
}
```

## 5. Implementation Patterns

### 5.1. Error Handling

```java
// Exception hierarchy
public sealed class TileverseException extends Exception 
    permits FormatException, ProcessingException, IOException { ... }

public final class FormatException extends TileverseException { ... }
public final class ProcessingException extends TileverseException { ... }
public final class IOException extends TileverseException { ... }

// Usage
try {
    // Operation that might fail
} catch (FormatException e) {
    // Handle format errors
} catch (ProcessingException e) {
    // Handle processing errors
} catch (IOException e) {
    // Handle I/O errors
} catch (TileverseException e) {
    // Handle any other library exceptions
}
```

### 5.2. Resource Management

```java
// Auto-closeable resources
public class PMTilesReader implements Closeable {
    private final FileChannel channel;
    
    @Override
    public void close() throws IOException {
        channel.close();
    }
}

// Usage with try-with-resources
try (PMTilesReader reader = new PMTilesReader(path);
     PMTilesWriter writer = new PMTilesWriter(outPath)) {
    // Use resources
}
```

### 5.3. Builders

```java
// Builder pattern implementation
public class PMTilesBuilderImpl implements PMTilesBuilder {
    private DataSource source;
    private Path destination;
    private byte minZoom = 0;
    private byte maxZoom = 14;
    private String layerName = "default";
    private SimplificationMethod simplificationMethod = SimplificationMethod.DOUGLAS_PEUCKER;
    private double simplificationTolerance = 1.0;
    // Other configuration fields
    
    @Override
    public PMTilesBuilder source(DataSource source) {
        this.source = source;
        return this;
    }
    
    // Other builder methods
    
    @Override
    public PMTiles build() {
        // Validate configuration
        Objects.requireNonNull(source, "Source cannot be null");
        Objects.requireNonNull(destination, "Destination cannot be null");
        
        // Create and return implementation
        return new PMTilesImpl(
            source,
            destination,
            minZoom,
            maxZoom,
            layerName,
            simplificationMethod,
            simplificationTolerance,
            // Other configuration
        );
    }
}
```

### 5.4. Immutable Data Types

```java
// Immutable record
public record ZXY(byte z, int x, int y) {
    // Validation in canonical constructor
    public ZXY {
        if (z < 0) {
            throw new IllegalArgumentException("Zoom level must be non-negative");
        }
        
        int maxCoord = (1 << z) - 1;
        if (x < 0 || x > maxCoord) {
            throw new IllegalArgumentException("X coordinate out of range");
        }
        if (y < 0 || y > maxCoord) {
            throw new IllegalArgumentException("Y coordinate out of range");
        }
    }
    
    // Additional methods
}
```

## 6. Performance Considerations

### 6.1. Memory Management

- Use streaming APIs for large datasets
- Implement batch processing for tile generation
- Use temporary files for intermediate data
- Consider memory-mapped files for large operations

### 6.2. Parallelism

- Process independent tiles in parallel
- Use Java 21's virtual threads for I/O operations
- Implement fork/join patterns for recursive operations
- Balance thread pools based on CPU cores

### 6.3. I/O Optimization

- Use nio for efficient I/O operations
- Implement buffering for file operations
- Use direct buffers for performance-critical operations
- Batch related I/O operations

### 6.4. Algorithmic Efficiency

- Optimize spatial indexing
- Implement efficient simplification algorithms
- Use appropriate data structures for spatial operations
- Optimize tile lookup with binary search

## 7. Testing Strategy

### 7.1. Unit Testing

- Test individual components in isolation
- Use mockito for mocking dependencies
- Cover edge cases and failure scenarios
- Validate against known examples

### 7.2. Integration Testing

- Test end-to-end workflows
- Verify compatibility with the specification
- Test with real-world data
- Validate with external tools

### 7.3. Performance Testing

- Benchmark critical operations
- Test with large datasets
- Compare against reference implementations
- Establish performance baselines

### 7.4. Compatibility Testing

- Verify compatibility with PMTiles specification
- Test with various consumers (web maps, etc.)
- Validate against reference files
- Test cross-platform compatibility

## 8. Evolution and Maintenance

### 8.1. Version Compatibility

- Maintain backwards compatibility
- Support multiple PMTiles versions
- Clear deprecation policies
- Documented migration paths

### 8.2. Extension Mechanisms

- Plugin architecture for custom components
- Service Provider Interface (SPI) for extensions
- Clear extension points
- Documentation for extension development

### 8.3. Future Directions

- Support for 3D tiles
- Integration with cloud storage
- Real-time tile generation
- Advanced styling options

## 9. Conclusion

The Tileverse architecture emphasizes modularity, extensibility, and performance. By separating concerns into distinct modules and defining clear interfaces, the library provides a foundation that can evolve while maintaining compatibility and performance.

The design principles of immutability, separation of concerns, and progressive disclosure create a library that is both easy to use for simple cases and powerful enough for advanced scenarios. The consistent use of modern Java features like records, sealed classes, and pattern matching makes the code more concise and robust.

This architecture provides a solid foundation for implementing a complete Java solution for PMTiles, capable of handling large datasets efficiently while providing a clean, intuitive API for developers.