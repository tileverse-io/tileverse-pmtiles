# PMTiles Format Specification

This document provides a detailed specification of the PMTiles format as implemented in Tippecanoe and our Java library. It focuses on the binary format, data structures, and algorithms used in PMTiles version 3.

## 1. File Structure

A PMTiles file consists of five main sections:

1. **Header** (127 bytes)
2. **Root Directory**
3. **JSON Metadata**
4. **Leaf Directories** (optional)
5. **Tile Data**

### 1.1. Header (127 bytes)

The header is a fixed-size structure at the beginning of the file containing essential metadata:

| Offset | Size (bytes) | Type   | Description                         |
|--------|--------------|--------|-------------------------------------|
| 0      | 7            | ASCII  | Magic number: "PMTiles"             |
| 7      | 1            | uint8  | Version: 3                          |
| 8      | 8            | uint64 | Root directory offset               |
| 16     | 8            | uint64 | Root directory length               |
| 24     | 8            | uint64 | JSON metadata offset                |
| 32     | 8            | uint64 | JSON metadata length                |
| 40     | 8            | uint64 | Leaf directories offset             |
| 48     | 8            | uint64 | Leaf directories length             |
| 56     | 8            | uint64 | Tile data offset                    |
| 64     | 8            | uint64 | Tile data length                    |
| 72     | 8            | uint64 | Number of addressed tiles           |
| 80     | 8            | uint64 | Number of tile entries              |
| 88     | 8            | uint64 | Number of tile contents             |
| 96     | 1            | uint8  | Clustered flag (0 or 1)             |
| 97     | 1            | uint8  | Internal compression type           |
| 98     | 1            | uint8  | Tile compression type               |
| 99     | 1            | uint8  | Tile type                           |
| 100    | 1            | uint8  | Minimum zoom level                  |
| 101    | 1            | uint8  | Maximum zoom level                  |
| 102    | 4            | int32  | Minimum longitude (E7 format)       |
| 106    | 4            | int32  | Minimum latitude (E7 format)        |
| 110    | 4            | int32  | Maximum longitude (E7 format)       |
| 114    | 4            | int32  | Maximum latitude (E7 format)        |
| 118    | 1            | uint8  | Center zoom level                   |
| 119    | 4            | int32  | Center longitude (E7 format)        |
| 123    | 4            | int32  | Center latitude (E7 format)         |

**Notes:**
- All multi-byte values are stored in little-endian order
- E7 format means the value is multiplied by 10,000,000 (7 decimal places) to convert float to integer

**Compression Type Constants:**
- `0x0`: Unknown
- `0x1`: None (uncompressed)
- `0x2`: GZIP
- `0x3`: Brotli
- `0x4`: Zstandard

**Tile Type Constants:**
- `0x0`: Unknown
- `0x1`: MVT (Mapbox Vector Tile)
- `0x2`: PNG
- `0x3`: JPEG
- `0x4`: WebP

### 1.2. Directories

Directories map tile IDs to data locations. They consist of entries that are delta-encoded and compressed for efficiency.

#### 1.2.1. Directory Entry Structure

Each directory entry conceptually contains:

- **Tile ID**: Unique identifier for a tile (derived from z/x/y coordinates)
- **Offset**: Byte offset in the tile data or leaf directory section
- **Length**: Length of the tile data or leaf directory in bytes
- **Run Length**: Number of consecutive tiles with identical content (0 for leaf directory entries)

#### 1.2.2. Directory Serialization Format

Directories are serialized as follows:

1. **Number of entries** (varint)
2. **Delta-encoded tile IDs** (varints)
   - First tile ID is absolute
   - Subsequent IDs are stored as differences from the previous ID
3. **Run lengths** (varints)
   - One value per entry
4. **Lengths** (varints)
   - One value per entry
5. **Offsets** (varints with optimization)
   - If an entry's offset follows the previous entry's data, encode as 0
   - Otherwise, encode as (offset + 1)

#### 1.2.3. Varints

Variable-length integers (varints) are used to efficiently encode integers:

- Each byte uses 7 bits for data and 1 bit to indicate continuation
- The most significant bit (MSB) is set to 1 if more bytes follow, 0 for the last byte
- The value is constructed by concatenating the 7-bit chunks, least significant first

Example encoding 300 (0x12C):
```
0x01001100 0x00000010
  ^       ^
  |       Last byte (MSB = 0)
  Continuation (MSB = 1)
```

#### 1.2.4. Two-Level Directory Structure

For large tilesets:
- Root directory contains entries pointing to leaf directories
- Leaf directory entries have run_length = 0
- Leaf directories contain entries pointing to tile data
- This keeps the root directory size manageable (typically under 16KB)

### 1.3. JSON Metadata

The JSON metadata section contains information about the tileset:

```json
{
  "name": "My Tileset",
  "format": "pbf",
  "type": "overlay",
  "description": "Description of the tileset",
  "version": "1.0",
  "attribution": "© Contributors",
  "strategies": [...],
  "tippecanoe_decisions": {...},
  "generator": "Tippecanoe",
  "generator_options": "Command line options used",
  "antimeridian_adjusted_bounds": "-180,-85,180,85",
  "vector_layers": [
    {
      "id": "layer_name",
      "description": "Layer description",
      "minzoom": 0,
      "maxzoom": 14,
      "fields": {
        "attr1": "string",
        "attr2": "number"
      }
    }
  ],
  "tilestats": {...}
}
```

### 1.4. Tile Data

The tile data section contains the actual tile content, typically in Mapbox Vector Tile (MVT) format, but can also be raster formats (PNG, JPEG, WebP).

- Tiles are usually compressed (GZIP, Brotli, etc.)
- Identical tiles are stored only once and referenced multiple times in the directory

## 2. Tile Coordinates and IDs

### 2.1. Z/X/Y Coordinates

PMTiles uses the standard web mapping tile coordinate system:
- Z: Zoom level (0 to 32)
- X: Column (0 to 2^z - 1)
- Y: Row (0 to 2^z - 1)

The top-left tile is (0,0), and Y increases southward.

### 2.2. Tile ID Calculation

Tile IDs are calculated to optimize spatial locality using a Hilbert curve:

1. **Accumulate the tiles in previous zoom levels:**
   ```
   acc = 0
   for (t_z = 0; t_z < z; t_z++)
       acc += (1 << t_z) * (1 << t_z)
   ```

2. **Convert (x,y) to Hilbert curve position at zoom level z:**
   ```
   n = 1 << z  // Size of grid at this zoom
   d = 0       // Position on curve
   
   for (s = n/2; s > 0; s /= 2) {
       rx = (x & s) > 0 ? 1 : 0
       ry = (y & s) > 0 ? 1 : 0
       d += s * s * ((3 * rx) ^ ry)
       rotate(s, rx, ry, x, y)  // Rotate/flip quadrant
   }
   ```

3. **Final tile ID:** `tileId = acc + d`

The `rotate` function handles the appropriate rotations for the Hilbert curve calculation.

### 2.3. Inverse Tile ID Calculation

To convert a tile ID back to z/x/y:

1. **Find the zoom level:**
   ```
   acc = 0
   for (z = 0; z < 32; z++) {
       num_tiles = (1 << z) * (1 << z)
       if (acc + num_tiles > tileId)
           return tileOnLevel(z, tileId - acc)
       acc += num_tiles
   }
   ```

2. **Convert Hilbert curve position to (x,y):**
   ```
   n = 1 << z
   t = pos  // Position on Hilbert curve
   x = 0
   y = 0
   
   for (s = 1; s < n; s *= 2) {
       rx = 1 & (t / 2)
       ry = 1 & (t ^ rx)
       rotate(s, x, y, rx, ry)  // Inverse rotation
       x += s * rx
       y += s * ry
       t /= 4
   }
   ```

## 3. Reading Algorithm

The process for reading a tile from a PMTiles file:

1. **Read header** (first 127 bytes)
2. **Calculate tile ID** from z/x/y coordinates
3. **Search root directory:**
   - Binary search for tile ID or containing range
   - If entry has run_length > 0, it points directly to tile data
   - If entry has run_length = 0, it points to a leaf directory
4. **If leaf directory is needed:**
   - Read and decompress leaf directory
   - Binary search for tile ID in leaf directory
5. **Read tile data:**
   - Calculate absolute offset: tile_data_offset + entry.offset
   - Read entry.length bytes
   - Decompress if necessary

## 4. Writing Algorithm

The process for creating a PMTiles file:

1. **Collect all tiles** with their z/x/y coordinates
2. **Convert to tile IDs** and sort
3. **Deduplicate identical tiles**
4. **Build directory entries:**
   - Assign offsets in the tile data section
   - Calculate run lengths for consecutive identical tiles
5. **Build directory structure:**
   - If all entries fit in root directory, use single-level structure
   - Otherwise, build two-level structure with leaf directories
6. **Calculate offsets** for all sections
7. **Write header** with appropriate values
8. **Write root directory, metadata, leaf directories, and tile data**

## 5. Optimizations

### 5.1. Run-Length Encoding

Consecutive tiles with identical content are encoded with a run length:
- Single entry with run_length > 1
- Reduces directory size
- Common in raster tilesets with empty/ocean tiles

### 5.2. Spatial Ordering

Tile IDs based on the Hilbert curve preserve spatial locality:
- Adjacent tiles in space tend to be adjacent in the file
- Improves compression ratios
- More efficient access patterns for typical usage

### 5.3. Two-Level Directory

For large tilesets, the two-level directory structure:
- Keeps the root directory small (typically under 16KB)
- Allows efficient random access
- Reduces memory requirements for readers

### 5.4. Offset Optimization

In directory serialization, consecutive offsets are optimized:
- If an entry follows the previous one, encode offset as 0
- Reduces directory size
- Common in well-ordered tilesets

## 6. PMTiles vs. MBTiles

PMTiles was designed as an alternative to MBTiles with several advantages:

| Feature                    | PMTiles                          | MBTiles                          |
|----------------------------|----------------------------------|----------------------------------|
| **Format**                 | Single file                      | SQLite database                  |
| **Random access**          | Optimized for cloud storage      | Requires complete file download  |
| **Directory structure**    | Custom binary format             | SQLite indexes                   |
| **Spatial locality**       | Preserved with Hilbert curves    | Not preserved                    |
| **Size optimization**      | Run-length encoding, deduplication | Less optimized                 |
| **Network efficiency**     | Range requests                   | Full file transfers              |
| **Update support**         | Limited (append or recreate)     | Better for incremental updates   |
| **Complexity**             | Specialized binary format        | Standard SQLite                  |

## 7. Implementation Considerations

When implementing a PMTiles reader or writer:

### 7.1. Reading

- Support range requests for efficient cloud access
- Cache directory data for repeated access
- Handle compression properly
- Validate header and directory structures

### 7.2. Writing

- Sort tiles by tile ID for optimal organization
- Implement efficient run-length encoding
- Balance directory levels based on tileset size
- Use appropriate compression for different sections

### 7.3. Memory Management

- Avoid loading the entire file into memory
- Use streaming for large operations
- Consider memory-mapped files for efficient access

## 8. Future Extensions

Potential extensions to the PMTiles format:

1. **Incremental update support**
   - Adding tiles without recreating the entire file
   
2. **Multiple tile formats**
   - Supporting different formats in the same file
   
3. **Advanced compression**
   - Differential encoding between tiles
   - Shared dictionaries for improved compression

4. **Extended metadata**
   - Style information
   - Extended attribution
   - Additional tileset parameters

## Conclusion

The PMTiles format offers an efficient, cloud-optimized alternative to traditional tile storage formats. Its specialized binary structure, spatial ordering, and compression optimizations make it particularly suitable for large tilesets delivered over networks. By understanding the detailed specification, developers can implement compatible readers and writers across different platforms and programming languages.