# PMTiles Creation Process

This document explains the detailed process of how PMTiles files are created based on the Tippecanoe implementation. It covers the end-to-end pipeline from input data processing to final PMTiles file creation.

## 1. PMTiles File Format Overview

PMTiles is a single-file format for storing map tiles, optimized for cloud storage and efficient random access. The format consists of:

### 1.1. File Structure

1. **Header (127 bytes)**
   - Magic number (`PMTiles`)
   - Version number (3)
   - Offsets and sizes for directories and data
   - Metadata like bounds, zoom levels, compression type

2. **Root Directory**
   - Contains entries mapping tile IDs to either:
     - Tile data offsets and lengths
     - Leaf directory offsets and lengths

3. **JSON Metadata**
   - Contains information about the tileset (JSON format)
   - Layer definitions, attribution, etc.

4. **Leaf Directories** (optional)
   - Additional directory structure for larger tilesets
   - Used when root directory would exceed size limits

5. **Tile Data**
   - The actual compressed tile content (typically MVT format)

### 1.2. Directory Structure

PMTiles uses a specialized directory structure for efficient look-up:

- **Directory entries**: Each entry contains `tile_id`, `offset`, `length`, and `run_length`
- **Tile IDs**: Derived from z/x/y coordinates using a Hilbert curve for locality
- **Run-length encoding**: Consecutive identical tiles are stored once with a run length
- **Leaf directories**: For large datasets, a two-level directory structure is used

## 2. Input Data Processing

### 2.1. Reading Source Data

Tippecanoe supports various input formats:

- GeoJSON (individual features or FeatureCollection)
- Shapefiles
- CSV with geographic coordinates
- Other formats via conversion tools

The data is read and parsed into an internal feature representation, which includes:
- Geometry (point, line, polygon)
- Properties (attributes)
- Metadata (layer information, etc.)

### 2.2. Feature Pre-processing

Before tile generation, features go through several pre-processing steps:

1. **Coordinate transformation**
   - Geographic coordinates (lon/lat) to Web Mercator projection
   - Handling of antimeridian crossing
   
2. **Feature filtering**
   - Removal of unwanted attributes
   - Feature selection based on criteria
   
3. **Feature simplification**
   - Douglas-Peucker or Visvalingam algorithm for line/polygon simplification
   - Adjustable based on zoom level and detail settings

4. **Attribute transformation**
   - Type conversion (string to number, etc.)
   - Attribute renaming or calculation

## 3. Tile Generation

### 3.1. Zoom Level Pyramid

The tile creation process starts at the maximum zoom level and works backward:

1. **Maximum zoom placement**
   - Features are placed in tiles at the maximum zoom level
   - Coordinates are converted to tile-local coordinates

2. **Tile subdivision**
   - The geographic space is divided into a grid of 2^z × 2^z tiles at zoom level z
   - Each feature is assigned to the tiles it intersects

3. **Lower zoom generation**
   - Features are propagated to lower zoom levels with increasing simplification
   - At each level, features may be filtered or simplified more aggressively

### 3.2. Feature Placement and Clipping

For each tile:

1. **Geometry intersection**
   - Determine which features intersect with the tile
   - Clip polygons and lines to tile boundaries

2. **Coordinate transformation**
   - Transform coordinates to tile-local integer coordinates
   - Scale to appropriate precision (usually 4096 × 4096 grid)

3. **Feature merging**
   - Features may be merged if they have identical properties
   - Simplification of coincident borders between polygons

### 3.3. Feature Reduction Strategies

For dense data at lower zoom levels, various strategies are used:

1. **Dropping features**
   - Based on importance, size, or other attributes
   - Progressive dropping at lower zooms

2. **Feature coalescing**
   - Combining multiple features into representatives
   - Aggregating attributes (sum, mean, etc.)

3. **Clustering**
   - Using clustering algorithms to reduce point density
   - Creating representative points for feature groups

4. **Attribute aggregation**
   - Computing statistics over dropped features
   - Preserving important information in aggregated form

## 4. Vector Tile Creation

### 4.1. MVT Encoding

For each tile:

1. **Feature conversion**
   - Internal features are converted to vector tile features
   - Geometry is encoded using the vector tile spec commands

2. **Command encoding**
   - MoveTo, LineTo, and ClosePath commands
   - Integer delta encoding for efficiency

3. **Layer organization**
   - Features are grouped into layers
   - Each layer has independent attributes

4. **Attribute encoding**
   - String values are added to a string table
   - Values are encoded as integers, floats, strings, or boolean

### 4.2. Tile Compression

1. **Individual tile compression**
   - Usually GZIP compression
   - Can also use other formats (Brotli, Zstandard)

2. **Size optimization**
   - Removing duplicate vertices
   - Simplifying geometry appropriate to zoom level
   - Minimizing attribute redundancy

## 5. PMTiles Assembly

### 5.1. Tile Organization

1. **Tile ID calculation**
   - Z/X/Y coordinates are converted to a single tile ID using a Hilbert curve
   - This preserves spatial locality for better compression and access patterns

2. **Deduplication**
   - Identical tiles are stored only once
   - References maintained through directory entries

3. **Clustering**
   - Tiles are arranged by spatial proximity
   - Improves compression and access patterns for typical usage

### 5.2. Directory Creation

1. **Entry creation**
   - For each unique tile, create a directory entry
   - Include tile ID, offset, length, and run length

2. **Run-length encoding**
   - Consecutive tiles with identical content are encoded with a run length
   - Reduces directory size for repeated tiles

3. **Two-level directory**
   - If the root directory exceeds the size limit (typically 16KB):
     - Split into leaf directories
     - Create root directory entries pointing to leaf directories

### 5.3. File Assembly

Once all components are prepared, the PMTiles file is assembled:

1. **Header writing**
   - Write the 127-byte header with metadata and offsets
   - Include bounds, zoom ranges, and compression information

2. **Root directory**
   - Serialize and compress the root directory
   - Write at the specified offset

3. **JSON metadata**
   - Serialize and compress the JSON metadata
   - Write at the specified offset

4. **Leaf directories** (if needed)
   - Serialize and compress leaf directories
   - Write at the specified offset

5. **Tile data**
   - Write all unique compressed tiles
   - Maintain offsets for directory references

## 6. Optimizations in Tippecanoe

Tippecanoe implements several optimizations for efficient PMTiles creation:

### 6.1. Memory Efficiency

1. **Streaming processing**
   - Features are processed incrementally
   - Not all features need to be in memory simultaneously

2. **Temporary storage**
   - Intermediate data may be stored on disk
   - MBTiles format is used as a temporary store before conversion to PMTiles

3. **Efficient data structures**
   - Custom containers optimized for geospatial data
   - Spatial indexing for quick feature lookup

### 6.2. Performance Optimizations

1. **Parallel processing**
   - Multi-threaded tile generation
   - Independent tiles can be processed concurrently

2. **Optimized algorithms**
   - Fast geometric operations
   - Efficient simplification routines

3. **Incremental updates**
   - Ability to update existing tilesets
   - Avoiding complete regeneration for small changes

### 6.3. Quality Optimizations

1. **Preserve topology**
   - Special handling of shared borders
   - Consistent simplification across adjacent features

2. **Attribute preservation**
   - Intelligent retention of important attributes
   - Statistical aggregation for dropped features

3. **Customizable simplification**
   - Different algorithms for different feature types
   - Adaptable detail based on feature importance

## 7. PMTiles Conversion in Tippecanoe

Tippecanoe converts from MBTiles (SQLite database) to PMTiles in this process:

1. **MBTiles scanning**
   - Read all tile entries from the `map` table
   - Convert to tile IDs using z/x/y coordinates

2. **Tile ordering**
   - Sort tiles by tile ID (Hilbert curve order)
   - Group identical tiles via hash references

3. **Directory building**
   - Create directory entries with run-length encoding
   - Build two-level directory if needed

4. **Metadata conversion**
   - Convert MBTiles metadata to PMTiles JSON format
   - Include layer information, attribution, bounds

5. **File assembly**
   - Write all components in the PMTiles format
   - Calculate and update header offsets

## 8. Java Implementation Considerations

When implementing PMTiles creation in Java, consider:

### 8.1. Memory Management

- Use streaming APIs for large datasets
- Implement efficient spatial indexing
- Consider memory-mapped files for large operations

### 8.2. Performance

- Utilize Java 21 features like virtual threads for parallelism
- Optimize geometry operations using JTS efficiently
- Implement custom serialization for tile data

### 8.3. API Design

- Provide a clean builder pattern for configuration
- Support progress reporting and cancellation
- Implement resource management with try-with-resources

### 8.4. Extensibility

- Allow custom simplification algorithms
- Support different input and output formats
- Enable feature filtering and transformation hooks

## 9. Conclusion

PMTiles creation in Tippecanoe involves a sophisticated pipeline from raw geographic data to an optimized, cloud-friendly tile format. The process combines various techniques:

- Geometric processing and simplification
- Hierarchical zoom level handling
- Spatial ordering with Hilbert curves
- Efficient directory structures
- Compression and deduplication

By understanding these techniques, we can implement a robust Java version that maintains the performance and flexibility of the original C++ implementation while leveraging Java's strengths in memory management, concurrency, and API design.