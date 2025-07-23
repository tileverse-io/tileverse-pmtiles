# Map Tile Format Comparison

This document compares PMTiles with other common tile storage and serving formats, highlighting the strengths and weaknesses of each approach.

## 1. Overview of Tile Formats

| Format | Type | Structure | Primary Use Case | Notable Users |
|--------|------|-----------|------------------|---------------|
| **PMTiles** | Single file | Binary container with optimized directory | Cloud-optimized delivery | Protomaps, Felt |
| **MBTiles** | SQLite database | Tables for metadata and tiles | Local/server storage | Mapbox, QGIS, Mobile apps |
| **Directory/XYZ** | File hierarchy | z/x/y.ext directory structure | Web serving | OpenStreetMap, Google Maps |
| **GeoPackage** | SQLite database | OGC standard with multiple tables | GIS interoperability | ArcGIS, QGIS |
| **TileJSON** | Directory + JSON | XYZ tiles with metadata JSON | Web services | Mapbox, OpenMapTiles |
| **Cloud Optimized GeoTIFF** | Single file | GeoTIFF with optimized structure | Raster data | Earth observation, satellite imagery |

## 2. Detailed Comparison

### 2.1. PMTiles

**Structure:**
- Single binary file
- Fixed-size header
- Optimized directory structure using Hilbert curves
- Optional two-level directory for large tilesets
- Compressed tile data section

**Advantages:**
- Optimized for cloud storage and HTTP range requests
- Efficient spatial organization with Hilbert curves
- Run-length encoding for repetitive tiles
- Compact directory structure
- No server-side software required

**Disadvantages:**
- Relatively new format with fewer tools
- More complex to implement than simple formats
- Limited support for incremental updates
- Less standardized than OGC formats

**Best for:**
- Cloud-hosted vector tiles
- Static tilesets with infrequent updates
- Serverless deployments
- Progressive web apps

### 2.2. MBTiles

**Structure:**
- SQLite database
- Metadata table for tileset information
- Tiles/Map/Images tables for tile storage
- Indexes for efficient retrieval

**Advantages:**
- Well-established format with broad tool support
- Good for local and server-based usage
- Supports both raster and vector tiles
- Easy to query and modify with SQL
- Good for incremental updates

**Disadvantages:**
- Not optimized for cloud storage
- Requires downloading entire file for remote usage
- SQLite overhead for simple applications
- Less efficient for very large tilesets

**Best for:**
- Local applications and analysis
- Mobile apps with offline maps
- Server-based tile delivery
- Datasets with frequent incremental updates

### 2.3. Directory/XYZ Structure

**Structure:**
- Hierarchical directory structure
- z/x/y.ext naming pattern
- Optional metadata files
- Plain files in filesystem

**Advantages:**
- Simplest possible structure
- Universal support across platforms
- Direct file access for web servers
- Easy to understand and debug
- Simple to update individual tiles

**Disadvantages:**
- Inefficient for large numbers of tiles (filesystem limits)
- No built-in metadata or compression
- Poor for transferring complete tilesets
- No spatial optimization

**Best for:**
- Simple web map serving
- Development and testing
- Small to medium tilesets
- Traditional web server deployment

### 2.4. GeoPackage

**Structure:**
- SQLite database following OGC standard
- Multiple tables for different data types
- Support for vector features, raster tiles, and attributes
- Standardized metadata and spatial reference systems

**Advantages:**
- OGC standard with excellent interoperability
- Supports multiple data types in one container
- Rich metadata and spatial reference support
- Well-supported in GIS software
- Suitable for both vector and raster data

**Disadvantages:**
- More complex than dedicated tile formats
- Less efficient for pure tile serving
- Not optimized for cloud deployment
- Overhead from comprehensive feature set

**Best for:**
- GIS applications requiring standards compliance
- Mixed vector/raster datasets
- Integration with spatial databases
- Complete geospatial data packaging

### 2.5. TileJSON

**Structure:**
- JSON metadata file describing tileset
- References to tile URLs (often in XYZ format)
- Can point to various backend storage options

**Advantages:**
- Simple, human-readable metadata
- Flexible backend storage
- Good for publishing tile services
- Easy integration with web maps
- Decouples metadata from storage

**Disadvantages:**
- Not a complete storage solution
- Requires additional server configuration
- Multiple files to manage
- No standardized tile storage format

**Best for:**
- Publishing tile services
- Documentation of tilesets
- Integration with web mapping libraries
- Describing existing tile collections

### 2.6. Cloud Optimized GeoTIFF (COG)

**Structure:**
- Standard GeoTIFF with optimized internal organization
- Includes overviews (pyramids)
- Special organization for HTTP range requests
- Internal tiling and compression

**Advantages:**
- Optimized for cloud storage and partial access
- Compatible with existing GeoTIFF tools
- Good for large raster datasets
- Built-in multi-resolution overview structure
- Strong support in remote sensing

**Disadvantages:**
- Raster-only (not for vector tiles)
- Less efficient for small tiles
- More complex than simple tile formats
- Specialized use case

**Best for:**
- Satellite and aerial imagery
- Raster analysis in cloud environments
- Scientific and earth observation data
- Large continuous raster datasets

## 3. Performance Characteristics

### 3.1. Storage Efficiency

| Format | Small Tilesets | Large Tilesets | Compression | Deduplication |
|--------|----------------|----------------|-------------|---------------|
| **PMTiles** | Good | Excellent | Built-in | Yes |
| **MBTiles** | Excellent | Good | Optional | Manual |
| **Directory** | Poor | Very Poor | External | No |
| **GeoPackage** | Good | Fair | Optional | No |
| **TileJSON** | Depends on backend | Depends on backend | Depends | Depends |
| **COG** | Fair | Excellent | Built-in | Limited |

### 3.2. Access Performance

| Format | Random Access | Sequential Access | Cloud Performance | Local Performance |
|--------|---------------|-------------------|-------------------|-------------------|
| **PMTiles** | Excellent | Good | Excellent | Good |
| **MBTiles** | Good | Excellent | Poor | Excellent |
| **Directory** | Good | Fair | Poor | Good |
| **GeoPackage** | Good | Good | Poor | Good |
| **TileJSON** | Depends | Depends | Depends | Depends |
| **COG** | Excellent | Good | Excellent | Good |

### 3.3. Update Characteristics

| Format | Single Tile Update | Batch Updates | Initial Creation Speed | Incremental Update Efficiency |
|--------|-------------------|---------------|------------------------|------------------------------|
| **PMTiles** | Poor | Poor | Good | Poor |
| **MBTiles** | Good | Good | Good | Good |
| **Directory** | Excellent | Good | Fair | Excellent |
| **GeoPackage** | Good | Good | Fair | Good |
| **TileJSON** | Depends | Depends | Depends | Depends |
| **COG** | Poor | Poor | Fair | Poor |

## 4. Technical Implementation Considerations

### 4.1. Implementation Complexity

| Format | Reader Complexity | Writer Complexity | Required Libraries | Format Specification Complexity |
|--------|-------------------|-------------------|-------------------|----------------------------------|
| **PMTiles** | Moderate | Moderate | Low | Moderate |
| **MBTiles** | Low | Low | SQLite | Low |
| **Directory** | Very Low | Very Low | None | Very Low |
| **GeoPackage** | High | High | SQLite, GDAL | High |
| **TileJSON** | Low | Very Low | JSON parser | Low |
| **COG** | High | High | GDAL, Imaging | High |

### 4.2. Development Ecosystem

| Format | Open Tools | Libraries | Documentation | Community Support |
|--------|------------|-----------|---------------|-------------------|
| **PMTiles** | Growing | JS, Rust, Python | Good | Growing |
| **MBTiles** | Extensive | Many languages | Excellent | Strong |
| **Directory** | Universal | Any | Minimal | Universal |
| **GeoPackage** | Extensive | GDAL, OGR | Excellent | Strong |
| **TileJSON** | Good | JS focused | Good | Strong |
| **COG** | Good | GDAL, specialized | Good | Strong |

## 5. Use Case Recommendations

### 5.1. Web Maps and Applications

- **Best option:** PMTiles for vector tiles, COG for raster tiles
- **Why:** Optimized for cloud delivery, efficient with CDNs, no server required
- **Alternative:** MBTiles with a tile server for frequently updated data

### 5.2. Mobile Applications

- **Best option:** MBTiles
- **Why:** Well-supported on mobile, good for offline use, efficient SQLite interface
- **Alternative:** PMTiles for static data with partial loading capabilities

### 5.3. GIS Integration

- **Best option:** GeoPackage
- **Why:** Standard format, excellent interoperability, rich metadata
- **Alternative:** MBTiles for simpler needs, COG for raster analysis

### 5.4. Large Dataset Publishing

- **Best option:** PMTiles for vector, COG for raster
- **Why:** Most efficient for large datasets, optimized cloud delivery
- **Alternative:** TileJSON + cloud storage for custom setups

### 5.5. Frequently Updated Maps

- **Best option:** Directory structure with CDN or server
- **Why:** Easiest to update individual tiles as needed
- **Alternative:** MBTiles with regular re-publishing

### 5.6. Development and Testing

- **Best option:** Directory structure
- **Why:** Simplest to work with, direct file access, easy debugging
- **Alternative:** MBTiles for more structured development

## 6. Hybrid Approaches

In practice, many systems use combinations of formats:

1. **Generation Pipeline:**
   - Generate tiles with Tippecanoe to MBTiles
   - Convert to PMTiles for distribution
   - Use with TileJSON for publishing

2. **Multi-format Publishing:**
   - Store master dataset in GeoPackage
   - Export to PMTiles for web distribution
   - Maintain XYZ for legacy compatibility

3. **Composite Services:**
   - Base layers in PMTiles/COG for efficiency
   - Overlay/dynamic data from live tile server
   - TileJSON to document the composite tileset

## 7. Future Trends

1. **Increased Cloud Optimization**
   - More formats adopting HTTP range request optimization
   - Integration with object storage best practices

2. **Vector Tile Dominance**
   - Shift from raster to vector for most web mapping
   - More efficient vector tile encodings and optimization

3. **Serverless Delivery**
   - Growing preference for formats that work without servers
   - Edge function integration for dynamic aspects

4. **Standards Convergence**
   - Movement toward standardization of cloud-optimized formats
   - Better interoperability between formats

5. **Metadata Enrichment**
   - More comprehensive metadata within tile formats
   - Better support for styling, attribution, and provenance

## 8. Conclusion

The choice of tile format depends heavily on specific use cases, deployment environments, and performance requirements. PMTiles excels in cloud-based delivery of static tilesets, while other formats have advantages in different scenarios.

For modern web mapping applications with cloud deployment, PMTiles represents one of the most efficient formats, especially for vector tiles. However, developers should consider the full spectrum of requirements, including update frequency, integration needs, and existing toolchains when selecting a format.

The Java Tileverse library aims to provide excellent support for PMTiles while maintaining awareness of the broader ecosystem of tile formats and their respective strengths and weaknesses.