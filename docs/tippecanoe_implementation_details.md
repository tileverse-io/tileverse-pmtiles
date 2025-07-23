# Tippecanoe PMTiles Implementation Details

This document provides comprehensive implementation details on how Tippecanoe generates PMTiles files, including algorithms, data structures, optimizations, and file format specifics.

## 1. PMTiles File Format Details

### 1.1. Header Structure (127 bytes)

The PMTiles header is exactly 127 bytes with this structure:

| Offset | Size | Type | Description |
|--------|------|------|-------------|
| 0 | 7 | ASCII | Magic number: "PMTiles" |
| 7 | 1 | uint8 | Version: 3 |
| 8 | 8 | uint64 | Root directory offset |
| 16 | 8 | uint64 | Root directory length |
| 24 | 8 | uint64 | JSON metadata offset |
| 32 | 8 | uint64 | JSON metadata length |
| 40 | 8 | uint64 | Leaf directories offset |
| 48 | 8 | uint64 | Leaf directories length |
| 56 | 8 | uint64 | Tile data offset |
| 64 | 8 | uint64 | Tile data length |
| 72 | 8 | uint64 | Number of addressed tiles |
| 80 | 8 | uint64 | Number of tile entries |
| 88 | 8 | uint64 | Number of tile contents |
| 96 | 1 | uint8 | Clustered flag (0 or 1) |
| 97 | 1 | uint8 | Internal compression type |
| 98 | 1 | uint8 | Tile compression type |
| 99 | 1 | uint8 | Tile type |
| 100 | 1 | uint8 | Minimum zoom level |
| 101 | 1 | uint8 | Maximum zoom level |
| 102 | 4 | int32 | Minimum longitude (E7 format) |
| 106 | 4 | int32 | Minimum latitude (E7 format) |
| 110 | 4 | int32 | Maximum longitude (E7 format) |
| 114 | 4 | int32 | Maximum latitude (E7 format) |
| 118 | 1 | uint8 | Center zoom level |
| 119 | 4 | int32 | Center longitude (E7 format) |
| 123 | 4 | int32 | Center latitude (E7 format) |

All multi-byte values are stored in little-endian order. Geographic coordinates use E7 format, where the value is multiplied by 10,000,000 to convert floating-point to integer.

### 1.2. Compression Types

Constants for compression types:
- `0x0`: Unknown
- `0x1`: None (uncompressed)
- `0x2`: GZIP
- `0x3`: Brotli
- `0x4`: Zstandard

### 1.3. Tile Types

Constants for tile types:
- `0x0`: Unknown
- `0x1`: MVT (Mapbox Vector Tile)
- `0x2`: PNG
- `0x3`: JPEG
- `0x4`: WebP

### 1.4. Directory Structure

Directories map tile IDs to data locations. Tippecanoe implements a specialized directory structure:

1. **Root Directory**: Starts immediately after the header (offset 127)
2. **JSON Metadata**: Contains information about the tileset
3. **Leaf Directories** (optional): Used when the root directory would be too large
4. **Tile Data**: The actual tile content

For large datasets, Tippecanoe uses a two-level directory structure:
- Root directory entries point to leaf directories
- Leaf directory entries point to tile data
- Leaf entries have run_length = 0 to indicate they are directories

### 1.5. Directory Serialization Format

Directories are serialized as:

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

### 1.6. Varint Encoding

Variable-length integers (varints) are used to efficiently encode integers:

- Each byte uses 7 bits for data and 1 bit to indicate continuation
- The most significant bit (MSB) is set to 1 if more bytes follow, 0 for the last byte
- The value is constructed by concatenating the 7-bit chunks, least significant first

Implementation in Tippecanoe:
```cpp
uint64_t decode_varint_impl(const char **data, const char *end) {
    const auto *begin = reinterpret_cast<const int8_t *>(*data);
    const auto *iend = reinterpret_cast<const int8_t *>(end);
    const int8_t *p = begin;
    uint64_t val = 0;

    if (iend - begin >= max_varint_length) {  // fast path
        do {
            int64_t b = *p++;
            val = ((uint64_t(b) & 0x7fU));
            if (b >= 0) {
                break;
            }
            b = *p++;
            val |= ((uint64_t(b) & 0x7fU) << 7U);
            // ...and so on for all bytes
        } while (false);
    } else {
        unsigned int shift = 0;
        while (p != iend && *p < 0) {
            val |= (uint64_t(*p++) & 0x7fU) << shift;
            shift += 7;
        }
        if (p == iend) {
            throw end_of_buffer_exception{};
        }
        val |= uint64_t(*p++) << shift;
    }

    *data = reinterpret_cast<const char *>(p);
    return val;
}
```

For writing varints:
```cpp
int write_varint(std::back_insert_iterator<std::string> data, uint64_t value) {
    int n = 1;

    while (value >= 0x80U) {
        *data++ = char((value & 0x7fU) | 0x80U);
        value >>= 7U;
        ++n;
    }
    *data = char(value);

    return n;
}
```

## 2. Tile ID Calculation

### 2.1. Hilbert Curve Algorithm

Tippecanoe uses a Hilbert curve for tile ID calculation to optimize spatial locality:

```cpp
// Convert ZXY to tile ID
uint64_t zxy_to_tileid(uint8_t z, uint32_t x, uint32_t y) {
    // Check bounds
    if (z > 31) {
        throw std::overflow_error("tile zoom exceeds 64-bit limit");
    }
    if (x > (1U << z) - 1 || y > (1U << z) - 1) {
        throw std::overflow_error("tile x/y outside zoom level bounds");
    }
    
    // Accumulate tiles in previous zoom levels
    uint64_t acc = 0;
    for (uint8_t t_z = 0; t_z < z; t_z++) 
        acc += (1LL << t_z) * (1LL << t_z);
    
    // Convert x,y to position on Hilbert curve
    int64_t n = 1LL << z;
    int64_t rx, ry, s, d = 0;
    int64_t tx = x;
    int64_t ty = y;
    
    for (s = n / 2; s > 0; s /= 2) {
        rx = (tx & s) > 0;
        ry = (ty & s) > 0;
        d += s * s * ((3LL * rx) ^ ry);
        rotate(s, tx, ty, rx, ry);
    }
    
    return acc + d;
}

// Helper function for Hilbert curve calculation
void rotate(int64_t n, int64_t &x, int64_t &y, int64_t rx, int64_t ry) {
    if (ry == 0) {
        if (rx == 1) {
            x = n - 1 - x;
            y = n - 1 - y;
        }
        int64_t t = x;
        x = y;
        y = t;
    }
}

// Convert tile ID back to ZXY
zxy tileid_to_zxy(uint64_t tileid) {
    uint64_t acc = 0;
    for (uint8_t t_z = 0; t_z < 32; t_z++) {
        uint64_t num_tiles = (1LL << t_z) * (1LL << t_z);
        if (acc + num_tiles > tileid) {
            return t_on_level(t_z, tileid - acc);
        }
        acc += num_tiles;
    }
    throw std::overflow_error("tile zoom exceeds 64-bit limit");
}

// Helper function to calculate ZXY at specific zoom level
zxy t_on_level(uint8_t z, uint64_t pos) {
    int64_t n = 1LL << z;
    int64_t rx, ry, s, t = pos;
    int64_t tx = 0;
    int64_t ty = 0;
    
    for (s = 1; s < n; s *= 2) {
        rx = 1LL & (t / 2);
        ry = 1LL & (t ^ rx);
        rotate(s, tx, ty, rx, ry);
        tx += s * rx;
        ty += s * ry;
        t /= 4;
    }
    
    return zxy(z, tx, ty);
}
```

## 3. PMTiles Generation Process

### 3.1. MBTiles to PMTiles Conversion

Tippecanoe primarily generates MBTiles first, then converts to PMTiles using this process:

```cpp
void mbtiles_map_image_to_pmtiles(char *fname, metadata m, bool tile_compression, bool fquiet, bool fquiet_progress) {
    // Open MBTiles database
    sqlite3 *db;
    sqlite3_open(fname, &db);
    
    // 1. Collect all tile IDs
    std::vector<uint64_t> tile_ids;
    // Query for all zoom/x/y values and convert to tile IDs
    // SQLite query: "SELECT zoom_level, tile_column, tile_row FROM map"
    
    // 2. Sort tile IDs for spatial locality
    std::stable_sort(tile_ids.begin(), tile_ids.end());
    
    // 3. Create hash to offset/length mapping and entries
    std::unordered_map<std::string, std::pair<unsigned long long, unsigned long>> hash_to_offset_len;
    std::vector<pmtiles::entryv3> entries;
    unsigned long long offset = 0;
    
    // 4. Process each tile, deduplicate by hash, and write to temp file
    for (auto const &tile_id : tile_ids) {
        pmtiles::zxy zxy = pmtiles::tileid_to_zxy(tile_id);
        
        // Get tile hash from map table
        // SQLite query: "SELECT tile_id FROM map WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?"
        
        if (hash exists in hash_to_offset_len) {
            // Reuse existing tile data
            auto offset_len = hash_to_offset_len.at(hash);
            
            // Check if this can extend a run-length encoding
            if (entries.size() > 0 && 
                tile_id == entries[entries.size() - 1].tile_id + 1 && 
                entries[entries.size() - 1].offset == offset_len.first) {
                
                // Extend run length
                entries[entries.size() - 1].run_length++;
            } else {
                // Add new entry
                entries.emplace_back(tile_id, offset_len.first, offset_len.second, 1);
            }
        } else {
            // Get tile data from images table
            // SQLite query: "SELECT tile_data FROM images WHERE tile_id = ?"
            
            // Write tile data to temp file
            // Add to hash mapping
            hash_to_offset_len.emplace(hash, std::make_pair(offset, len));
            
            // Add entry
            entries.emplace_back(tile_id, offset, len, 1);
            offset += len;
        }
    }
    
    // 5. Sort entries by tile ID
    std::stable_sort(entries.begin(), entries.end(), pmtiles::entryv3_cmp());
    
    // 6. Build directory structure
    std::string root_bytes;
    std::string leaves_bytes;
    int num_leaves;
    std::tie(root_bytes, leaves_bytes, num_leaves) = make_root_leaves(&compress_fn, pmtiles::COMPRESSION_GZIP, entries);
    
    // 7. Create header
    pmtiles::headerv3 header;
    
    // Set header fields from metadata
    header.min_zoom = m.minzoom;
    header.max_zoom = m.maxzoom;
    header.min_lon_e7 = m.minlon * 10000000;
    header.min_lat_e7 = m.minlat * 10000000;
    header.max_lon_e7 = m.maxlon * 10000000;
    header.max_lat_e7 = m.maxlat * 10000000;
    header.center_zoom = m.center_z;
    header.center_lon_e7 = m.center_lon * 10000000;
    header.center_lat_e7 = m.center_lat * 10000000;
    
    // Set other header fields
    header.clustered = 0x1;
    header.internal_compression = pmtiles::COMPRESSION_GZIP;
    header.tile_compression = tile_compression ? pmtiles::COMPRESSION_GZIP : pmtiles::COMPRESSION_NONE;
    header.tile_type = (m.format == "pbf") ? pmtiles::TILETYPE_MVT : 
                       (m.format == "png") ? pmtiles::TILETYPE_PNG : pmtiles::TILETYPE_UNKNOWN;
    
    // 8. Calculate offsets
    header.root_dir_offset = 127;  // Start after header
    header.root_dir_bytes = root_bytes.size();
    
    header.json_metadata_offset = header.root_dir_offset + header.root_dir_bytes;
    header.json_metadata_bytes = json_metadata.size();
    
    header.leaf_dirs_offset = header.json_metadata_offset + header.json_metadata_bytes;
    header.leaf_dirs_bytes = leaves_bytes.size();
    
    header.tile_data_offset = header.leaf_dirs_offset + header.leaf_dirs_bytes;
    header.tile_data_bytes = offset;
    
    header.addressed_tiles_count = tile_ids.size();
    header.tile_entries_count = entries.size();
    header.tile_contents_count = hash_to_offset_len.size();
    
    // 9. Write the final PMTiles file
    std::ofstream ostream(fname, std::ios::out | std::ios::binary);
    
    // Write header
    auto header_str = header.serialize();
    ostream.write(header_str.data(), header_str.length());
    
    // Write root directory
    ostream.write(root_bytes.data(), root_bytes.length());
    
    // Write JSON metadata
    ostream.write(json_metadata.data(), json_metadata.size());
    
    // Write leaf directories
    ostream.write(leaves_bytes.data(), leaves_bytes.length());
    
    // Write tile data from temp file
    // ...
    
    ostream.close();
}
```

### 3.2. Directory Creation

Tippecanoe builds root and leaf directories with this algorithm:

```cpp
std::tuple<std::string, std::string, int> make_root_leaves(
    const std::function<std::string(const std::string &, uint8_t)> mycompress, 
    uint8_t compression, 
    const std::vector<pmtiles::entryv3> &entries) {
    
    // First try with just a root directory
    auto test_bytes = pmtiles::serialize_directory(entries);
    auto compressed = mycompress(test_bytes, compression);
    
    // If the compressed root directory is small enough, use it directly
    if (compressed.size() <= 16384 - 127) {
        return std::make_tuple(compressed, "", 0);
    }
    
    // Otherwise, use a two-level directory structure
    int leaf_size = 4096;
    while (true) {
        std::string root_bytes;
        std::string leaves_bytes;
        int num_leaves;
        
        std::tie(root_bytes, leaves_bytes, num_leaves) = build_root_leaves(
            mycompress, compression, entries, leaf_size);
        
        // Check if root directory is now small enough
        if (root_bytes.length() < 16384 - 127) {
            return std::make_tuple(root_bytes, leaves_bytes, num_leaves);
        }
        
        // If not, increase leaf size and try again
        leaf_size *= 2;
    }
}

std::tuple<std::string, std::string, int> build_root_leaves(
    const std::function<std::string(const std::string &, uint8_t)> mycompress, 
    uint8_t compression, 
    const std::vector<pmtiles::entryv3> &entries, 
    int leaf_size) {
    
    std::vector<pmtiles::entryv3> root_entries;
    std::string leaves_bytes;
    int num_leaves = 0;
    
    // Process entries in chunks of leaf_size
    for (size_t i = 0; i < entries.size(); i += leaf_size) {
        num_leaves++;
        
        // Get chunk of entries for this leaf
        int end = i + leaf_size;
        if (end > entries.size()) {
            end = entries.size();
        }
        std::vector<pmtiles::entryv3> subentries = {
            entries.begin() + i, entries.begin() + end
        };
        
        // Serialize and compress the leaf
        auto uncompressed_leaf = pmtiles::serialize_directory(subentries);
        auto compressed_leaf = mycompress(uncompressed_leaf, compression);
        
        // Create root entry pointing to this leaf
        root_entries.emplace_back(
            entries[i].tile_id,       // Use first tile ID in the leaf
            leaves_bytes.size(),      // Offset in the leaf directories section
            compressed_leaf.size(),   // Size of the compressed leaf
            0                         // Run length 0 indicates it's a directory
        );
        
        // Append leaf to leaf directories
        leaves_bytes += compressed_leaf;
    }
    
    // Serialize and compress the root directory
    auto uncompressed_root = pmtiles::serialize_directory(root_entries);
    auto compressed_root = mycompress(uncompressed_root, compression);
    
    return std::make_tuple(compressed_root, leaves_bytes, num_leaves);
}
```

### 3.3. Directory Serialization

```cpp
std::string serialize_directory(const std::vector<entryv3> &entries) {
    std::string data;
    
    // Write number of entries
    write_varint(std::back_inserter(data), entries.size());
    
    // Write tile IDs (delta encoded)
    uint64_t last_id = 0;
    for (auto const &entry : entries) {
        write_varint(std::back_inserter(data), entry.tile_id - last_id);
        last_id = entry.tile_id;
    }
    
    // Write run lengths
    for (auto const &entry : entries) {
        write_varint(std::back_inserter(data), entry.run_length);
    }
    
    // Write lengths
    for (auto const &entry : entries) {
        write_varint(std::back_inserter(data), entry.length);
    }
    
    // Write offsets with optimization for consecutive entries
    for (size_t i = 0; i < entries.size(); i++) {
        if (i > 0 && entries[i].offset == entries[i - 1].offset + entries[i - 1].length) {
            write_varint(std::back_inserter(data), 0);
        } else {
            write_varint(std::back_inserter(data), entries[i].offset + 1);
        }
    }
    
    return data;
}

std::vector<entryv3> deserialize_directory(const std::string &decompressed) {
    const char *t = decompressed.data();
    const char *end = t + decompressed.size();
    
    // Read number of entries
    uint64_t num_entries = decode_varint(&t, end);
    
    std::vector<entryv3> result;
    result.resize(num_entries);
    
    // Read tile IDs (delta encoded)
    uint64_t last_id = 0;
    for (size_t i = 0; i < num_entries; i++) {
        uint64_t tile_id = last_id + decode_varint(&t, end);
        result[i].tile_id = tile_id;
        last_id = tile_id;
    }
    
    // Read run lengths
    for (size_t i = 0; i < num_entries; i++) {
        result[i].run_length = decode_varint(&t, end);
    }
    
    // Read lengths
    for (size_t i = 0; i < num_entries; i++) {
        result[i].length = decode_varint(&t, end);
    }
    
    // Read offsets with optimization for consecutive entries
    for (size_t i = 0; i < num_entries; i++) {
        uint64_t tmp = decode_varint(&t, end);
        
        if (i > 0 && tmp == 0) {
            result[i].offset = result[i - 1].offset + result[i - 1].length;
        } else {
            result[i].offset = tmp - 1;
        }
    }
    
    // Verify entire buffer was consumed
    if (t != end) {
        // Error: malformed directory
    }
    
    return result;
}
```

## 4. Optimizations in Tippecanoe

### 4.1. Tile Deduplication

Tippecanoe identifies and removes duplicate tiles:

1. It computes a hash for each tile's data
2. It maintains a map from hashes to file offsets and lengths
3. When a duplicate is found, it reuses the existing data instead of storing it again
4. The directory entries point to the same offset for duplicate tiles

```cpp
// Pseudo-code for deduplication
std::unordered_map<std::string, std::pair<offset, length>> hash_to_offset_len;

for (auto const &tile_id : tile_ids) {
    std::string tile_hash = get_tile_hash(tile_id);
    
    if (hash_to_offset_len.find(tile_hash) != hash_to_offset_len.end()) {
        // Reuse existing tile
        auto [existing_offset, existing_length] = hash_to_offset_len[tile_hash];
        entries.emplace_back(tile_id, existing_offset, existing_length, 1);
    } else {
        // Store new tile
        byte[] tile_data = get_tile_data(tile_id);
        hash_to_offset_len[tile_hash] = {current_offset, tile_data.length};
        entries.emplace_back(tile_id, current_offset, tile_data.length, 1);
        current_offset += tile_data.length;
    }
}
```

### 4.2. Run-Length Encoding

Tippecanoe uses run-length encoding for consecutive identical tiles:

1. When processing tile IDs in order, it checks if the current tile has the same content as the previous one
2. If they are consecutive tile IDs (n and n+1) and have the same content, it increases the run_length of the previous entry instead of creating a new one
3. This is especially effective for empty or ocean tiles in raster datasets

```cpp
// Pseudo-code for run-length encoding
if (entries.size() > 0 && 
    tile_id == entries.back().tile_id + 1 && 
    entries.back().offset == current_offset) {
    
    // Extend the run length of the previous entry
    entries.back().run_length++;
} else {
    // Create a new entry
    entries.emplace_back(tile_id, current_offset, length, 1);
}
```

### 4.3. Spatial Ordering with Hilbert Curves

Tippecanoe uses Hilbert curves to maintain spatial locality:

1. Tiles are sorted by their tile ID, which is calculated using a Hilbert curve
2. This ensures that spatially adjacent tiles are more likely to be stored close together
3. Improves compression ratios as similar tiles are grouped together
4. Creates more opportunities for run-length encoding

### 4.4. Two-Level Directory Structure

For large tilesets, Tippecanoe uses a two-level directory structure:

1. Root directory with entries pointing to leaf directories
2. Leaf directories with entries pointing to actual tiles
3. This keeps the root directory small (under 16KB) for efficient random access
4. The optimal leaf size is determined dynamically based on the total number of entries

### 4.5. Offset Optimization in Directory Serialization

When serializing directories, Tippecanoe optimizes how offsets are stored:

1. For consecutive entries, if an entry's offset is exactly after the previous entry, it stores 0 instead of the full offset
2. Otherwise, it stores (offset + 1) to distinguish from the 0 case
3. This reduces the directory size significantly when tiles are stored sequentially

### 4.6. Adaptive Compression

Tippecanoe supports different compression types for different parts of the file:

1. Directory structure typically uses GZIP compression
2. Tile data can use different compression types based on the content and user preference
3. The header stores the compression type for directories and tiles separately

## 5. Tile Reading Algorithm

### 5.1. Finding a Specific Tile

```cpp
std::pair<uint64_t, uint32_t> get_tile(
    const std::function<std::string(const std::string &, uint8_t)> decompress, 
    const char *pmtiles_map, 
    uint8_t z, 
    uint32_t x, 
    uint32_t y) {
    
    // Convert ZXY to tile ID
    uint64_t tile_id = pmtiles::zxy_to_tileid(z, x, y);
    
    // Read header
    std::string header_s{pmtiles_map, 127};
    auto h = pmtiles::deserialize_header(header_s);
    
    // Search in root directory
    uint64_t dir_offset = h.root_dir_offset;
    uint32_t dir_length = h.root_dir_bytes;
    
    for (int depth = 0; depth <= 3; depth++) {
        // Read and decompress directory
        std::string dir_s{pmtiles_map + dir_offset, dir_length};
        std::string decompressed_dir = decompress(dir_s, h.internal_compression);
        auto dir_entries = pmtiles::deserialize_directory(decompressed_dir);
        
        // Find entry for the tile ID
        auto entry = find_tile(dir_entries, tile_id);
        
        if (entry.length > 0) {
            if (entry.run_length > 0) {
                // This is a tile entry, return its location
                return std::make_pair(h.tile_data_offset + entry.offset, entry.length);
            } else {
                // This is a leaf directory entry, search in the leaf directory
                dir_offset = h.leaf_dirs_offset + entry.offset;
                dir_length = entry.length;
            }
        } else {
            // Tile not found
            return std::make_pair(0, 0);
        }
    }
    
    // Should not reach here
    return std::make_pair(0, 0);
}

// Find an entry for a tile ID in a directory
entryv3 find_tile(const std::vector<entryv3> &entries, uint64_t tile_id) {
    // Binary search for the entry
    int m = 0;
    int n = entries.size() - 1;
    
    while (m <= n) {
        int k = (n + m) >> 1;
        int cmp = tile_id - entries[k].tile_id;
        
        if (cmp > 0) {
            m = k + 1;
        } else if (cmp < 0) {
            n = k - 1;
        } else {
            return entries[k];
        }
    }
    
    // Check if the tile is in a run
    if (n >= 0) {
        if (entries[n].run_length == 0) {
            return entries[n];
        }
        if (tile_id - entries[n].tile_id < entries[n].run_length) {
            return entries[n];
        }
    }
    
    // Not found
    return entryv3{0, 0, 0, 0};
}
```

## 6. Additional Details from Tippecanoe

### 6.1. Metadata Creation

Tippecanoe creates JSON metadata with these components:

1. **Basic metadata**: name, format, type, description, version, attribution
2. **Layer information**: Details about each layer in the tileset
3. **Bounds**: Geographical bounds of the tileset
4. **Center**: Suggested center point and zoom level
5. **Tippecanoe-specific metadata**: Processing options and strategies used

### 6.2. Compression Functions

Tippecanoe implements compression and decompression functions:

```cpp
std::string decompress_fn(const std::string &input, uint8_t compression) {
    std::string output;
    if (compression == pmtiles::COMPRESSION_NONE) {
        output = input;
    } else if (compression == pmtiles::COMPRESSION_GZIP) {
        decompress(input, output);  // Uses zlib/gzip
    } else {
        throw std::runtime_error("Unknown or unsupported compression.");
    }
    return output;
}

std::string compress_fn(const std::string &input, uint8_t compression) {
    std::string output;
    if (compression == pmtiles::COMPRESSION_NONE) {
        output = input;
    } else if (compression == pmtiles::COMPRESSION_GZIP) {
        compress(input, output, true);  // Uses zlib/gzip
    } else {
        throw std::runtime_error("Unknown or unsupported compression.");
    }
    return output;
}
```

### 6.3. Error Handling

Tippecanoe defines several exception types for error handling:

- `pmtiles_magic_number_exception`: When the PMTiles header magic number is invalid
- `pmtiles_version_exception`: When the PMTiles version is not supported
- `varint_too_long_exception`: When a varint exceeds the maximum length
- `end_of_buffer_exception`: When trying to read past the end of a buffer

### 6.4. File Layout Calculation

Tippecanoe calculates file layout with these steps:

1. Header starts at position 0 (length 127 bytes)
2. Root directory starts at position 127
3. JSON metadata follows the root directory
4. Leaf directories (if any) follow the JSON metadata
5. Tile data follows the leaf directories
6. All offsets and lengths are stored in the header

## 7. Memory Management and Optimization

### 7.1. Streaming Process

Tippecanoe processes tiles in a streaming fashion:

1. It reads tiles from the MBTiles database one by one
2. It writes them to a temporary file
3. It only keeps the directory information in memory, not the actual tile data
4. This allows it to handle very large tilesets with limited memory

### 7.2. Temporary File Usage

Tippecanoe uses temporary files to manage memory:

1. Tile data is written to a temporary file
2. During final assembly, the data is copied from the temporary file to the PMTiles file
3. This avoids having to hold all tile data in memory

```cpp
// Create temporary file
std::string tmpname = (std::string(fname) + ".tmp");
std::ofstream tmp_ostream;
tmp_ostream.open(tmpname.c_str(), std::ios::out | std::ios::binary);

// Write tile data to temp file
tmp_ostream.write(blob, len);

// Later, during final assembly
std::ifstream tmp_istream(tmpname.c_str(), std::ios::in | std::ios_base::binary);
ostream << tmp_istream.rdbuf();
tmp_istream.close();
unlink(tmpname.c_str());  // Delete temp file
```

### 7.3. Buffered I/O

Tippecanoe uses buffered I/O for efficient reading and writing:

1. It opens files with appropriate buffer sizes
2. It uses efficient file copy operations
3. For large files, it uses memory-efficient streaming approaches

## 8. Implementation Notes for Java

When implementing these algorithms in Java, consider:

1. **Endianness**: All multi-byte values are stored in little-endian format, which requires explicit handling in Java
2. **Memory Management**: Use NIO ByteBuffers for efficient memory management
3. **File I/O**: Use FileChannel for random access I/O
4. **Varints**: Implement custom varint encoding and decoding
5. **Compression**: Use Java's built-in compression libraries or external libraries like Apache Commons Compress

## 9. Conclusion

This document provides a comprehensive overview of how Tippecanoe implements PMTiles creation, including:

1. Detailed file format specifications
2. Key algorithms for tile ID calculation and spatial ordering
3. Directory structure and serialization
4. Optimization techniques like deduplication and run-length encoding
5. Memory management strategies

With this information, you should be able to implement a fully compatible PMTiles generator in Java without needing to refer back to the Tippecanoe source code.