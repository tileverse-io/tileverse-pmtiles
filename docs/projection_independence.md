# Projection Independence in Tileverse

This document outlines the approach for making Tileverse projection-independent, distinguishing it from Tippecanoe's Web Mercator-centric approach and enabling better integration with GeoServer and GeoWebCache.

## 1. Background

### 1.1. Tippecanoe's Projection Approach

Tippecanoe implicitly uses Web Mercator (EPSG:3857) as the tile projection:

1. It converts all input coordinates from geographic (lon/lat) to Web Mercator
2. Tiles are generated in the standard XYZ tile scheme, which assumes Web Mercator
3. The tile extent calculations are specific to Web Mercator's characteristics
4. Antimeridian handling is built around Web Mercator's properties

This approach works well for web mapping but has limitations for GIS applications that require different coordinate reference systems.

### 1.2. GeoServer and GeoWebCache Requirements

GeoServer and GeoWebCache have more advanced projection requirements:

1. They support multiple coordinate reference systems for both data and tiles
2. Different tile pyramid schemes may be used for different projections
3. GeoServer's WMS and WMTS services allow clients to request data in various projections
4. GeoWebCache precomputes tiles in multiple projections for the same dataset

## 2. Projection Independence Architecture

### 2.1. Core Principles

Tileverse will implement projection independence through:

1. **Separation of geometry from tile pyramid**: Keep geometry handling independent from tile scheme logic
2. **Pluggable coordinate reference systems**: Support custom CRS without modifying core code
3. **Projection-aware tile indexing**: Adapt tile indexing based on the CRS being used
4. **Native input handling**: Process input data in its native projection when possible

### 2.2. Coordinate Reference System Interface

```java
/**
 * Defines a coordinate reference system and its properties.
 */
public interface CoordinateReferenceSystem {
    /**
     * Gets the identifier for this CRS.
     */
    String getIdentifier();
    
    /**
     * Gets a human-readable name for this CRS.
     */
    String getName();
    
    /**
     * Gets the bounds of this CRS in its native coordinates.
     */
    Bounds getBounds();
    
    /**
     * Checks if this CRS is compatible with the standard XYZ tile scheme.
     */
    boolean isCompatibleWithXYZScheme();
    
    /**
     * Gets the appropriate tile matrix set for this CRS.
     */
    TileMatrixSet getTileMatrixSet();
}
```

### 2.3. Tile Matrix Set Interface

```java
/**
 * Defines a tile pyramid scheme for a specific CRS.
 */
public interface TileMatrixSet {
    /**
     * Gets the identifier for this tile matrix set.
     */
    String getIdentifier();
    
    /**
     * Gets the CRS associated with this tile matrix set.
     */
    CoordinateReferenceSystem getCRS();
    
    /**
     * Gets the bounds of the tile matrix in CRS coordinates.
     */
    Bounds getBounds();
    
    /**
     * Gets the tile matrix for a specific zoom level.
     */
    TileMatrix getTileMatrix(int zoomLevel);
    
    /**
     * Converts coordinates in the CRS to a tile coordinate.
     */
    ZXY crsToTile(double x, double y, int zoom);
    
    /**
     * Converts a tile coordinate to bounds in CRS coordinates.
     */
    Bounds tileToCRSBounds(int z, int x, int y);
}
```

### 2.4. Tile Matrix Interface

```java
/**
 * Defines properties of a specific zoom level in a tile matrix set.
 */
public interface TileMatrix {
    /**
     * Gets the zoom level of this matrix.
     */
    int getZoomLevel();
    
    /**
     * Gets the scale denominator for this zoom level.
     */
    double getScaleDenominator();
    
    /**
     * Gets the pixel size in CRS units.
     */
    double getPixelSize();
    
    /**
     * Gets the width of tiles in this matrix in pixels.
     */
    int getTileWidth();
    
    /**
     * Gets the height of tiles in this matrix in pixels.
     */
    int getTileHeight();
    
    /**
     * Gets the number of columns (X) in this matrix.
     */
    int getMatrixWidth();
    
    /**
     * Gets the number of rows (Y) in this matrix.
     */
    int getMatrixHeight();
    
    /**
     * Gets the top-left corner point of the tile matrix in CRS coordinates.
     */
    Point getTopLeftCorner();
}
```

## 3. Standard Implementations

### 3.1. Web Mercator (EPSG:3857)

```java
public class WebMercatorCRS implements CoordinateReferenceSystem {
    @Override
    public String getIdentifier() {
        return "EPSG:3857";
    }
    
    @Override
    public String getName() {
        return "Web Mercator";
    }
    
    @Override
    public Bounds getBounds() {
        return new Bounds(
            -20037508.342789244, -20037508.342789244,
            20037508.342789244, 20037508.342789244
        );
    }
    
    @Override
    public boolean isCompatibleWithXYZScheme() {
        return true;
    }
    
    @Override
    public TileMatrixSet getTileMatrixSet() {
        return new WebMercatorTileMatrixSet();
    }
}
```

### 3.2. WGS84 (EPSG:4326)

```java
public class WGS84CRS implements CoordinateReferenceSystem {
    @Override
    public String getIdentifier() {
        return "EPSG:4326";
    }
    
    @Override
    public String getName() {
        return "WGS 84";
    }
    
    @Override
    public Bounds getBounds() {
        return new Bounds(-180, -90, 180, 90);
    }
    
    @Override
    public boolean isCompatibleWithXYZScheme() {
        return false; // Standard XYZ scheme is designed for Web Mercator
    }
    
    @Override
    public TileMatrixSet getTileMatrixSet() {
        return new WGS84TileMatrixSet();
    }
}
```

### 3.3. Custom Projections

The architecture allows for easy extension to support additional projections:

```java
public class ProjectedCRS implements CoordinateReferenceSystem {
    private final String identifier;
    private final String name;
    private final Bounds bounds;
    private final TileMatrixSet tileMatrixSet;
    
    public ProjectedCRS(String identifier, String name, Bounds bounds, TileMatrixSet tileMatrixSet) {
        this.identifier = identifier;
        this.name = name;
        this.bounds = bounds;
        this.tileMatrixSet = tileMatrixSet;
    }
    
    // Implementation of interface methods
}
```

## 4. Integration with Tile Processing

### 4.1. Projection-Aware Tile Generation

```java
public class TileGenerator {
    private final CoordinateReferenceSystem crs;
    private final TileMatrixSet tileMatrixSet;
    
    public TileGenerator(CoordinateReferenceSystem crs) {
        this.crs = crs;
        this.tileMatrixSet = crs.getTileMatrixSet();
    }
    
    public Collection<Tile> generateTiles(Geometry geometry, int minZoom, int maxZoom) {
        Collection<Tile> tiles = new ArrayList<>();
        
        // For each zoom level
        for (int z = minZoom; z <= maxZoom; z++) {
            TileMatrix matrix = tileMatrixSet.getTileMatrix(z);
            
            // Calculate tile coverage for this geometry
            Collection<ZXY> coverage = calculateTileCoverage(geometry, z, matrix);
            
            // Generate each tile
            for (ZXY zxy : coverage) {
                // Get tile bounds in CRS coordinates
                Bounds tileBounds = tileMatrixSet.tileToCRSBounds(zxy.z(), zxy.x(), zxy.y());
                
                // Clip geometry to tile bounds
                Geometry clipped = clipGeometry(geometry, tileBounds);
                
                // Generate tile content
                byte[] tileData = encodeTile(clipped, zxy, tileBounds);
                
                tiles.add(new Tile(zxy.z(), zxy.x(), zxy.y(), tileData));
            }
        }
        
        return tiles;
    }
    
    private Collection<ZXY> calculateTileCoverage(Geometry geometry, int zoom, TileMatrix matrix) {
        // Calculate which tiles intersect with the geometry at this zoom level
        // This will depend on the specific CRS and tile matrix
    }
    
    private Geometry clipGeometry(Geometry geometry, Bounds bounds) {
        // Clip the geometry to the bounds in the appropriate CRS
    }
    
    private byte[] encodeTile(Geometry geometry, ZXY zxy, Bounds bounds) {
        // Encode the geometry into a vector tile
        // Convert coordinates from CRS to tile-local coordinates
    }
}
```

### 4.2. API Integration

```java
public interface PMTilesBuilder {
    // Existing methods
    
    /**
     * Sets the coordinate reference system for the tiles.
     */
    PMTilesBuilder crs(CoordinateReferenceSystem crs);
    
    /**
     * Sets an explicit tile matrix set to use.
     */
    PMTilesBuilder tileMatrixSet(TileMatrixSet tileMatrixSet);
    
    // Build method
}
```

## 5. PMTiles Format Extensions

### 5.1. Extended Metadata

The PMTiles metadata needs to include information about the projection:

```json
{
  "name": "My Tileset",
  "format": "pbf",
  "crs": "EPSG:3857",
  "tile_matrix_set": "WebMercatorQuad",
  // Standard metadata fields
  "vector_layers": [...],
  // Additional projection information
  "projection_info": {
    "bounds": [-20037508.342789244, -20037508.342789244, 20037508.342789244, 20037508.342789244],
    "tile_origin": [-20037508.342789244, 20037508.342789244],
    "standard_scale_set": "GoogleMapsCompatible"
  }
}
```

### 5.2. Header Extensions

The PMTiles header remains compatible with the standard format, with projection information stored in the JSON metadata.

## 6. Integration with GeoServer and GeoWebCache

### 6.1. GeoServer Integration

```java
public class PMTilesReader implements TileReader {
    private final CoordinateReferenceSystem crs;
    private final PMTilesFile pmtiles;
    
    public PMTilesReader(Path path, CoordinateReferenceSystem crs) throws IOException {
        this.pmtiles = new PMTilesFile(path);
        this.crs = crs;
        
        // Verify compatibility
        if (!pmtiles.getMetadata().getCRS().equals(crs.getIdentifier())) {
            throw new IllegalArgumentException("PMTiles CRS does not match requested CRS");
        }
    }
    
    @Override
    public Tile readTile(int z, int x, int y) throws IOException {
        // Convert GeoServer tile coordinates to PMTiles coordinates if needed
        // This may involve flipping Y coordinates or other transformations
        
        // Read the tile from PMTiles
        return pmtiles.getTile(z, x, y);
    }
    
    // Other TileReader methods
}
```

### 6.2. GeoWebCache Integration

```java
public class PMTilesCacheStorage implements BlobStore {
    private final Path storagePath;
    
    @Override
    public void put(TileObject tile) throws StorageException {
        // Get required information
        String layerName = tile.getLayerName();
        String gridSetId = tile.getGridSetId();
        long[] tileIndex = tile.getXYZ();
        int z = (int) tileIndex[2];
        int x = (int) tileIndex[0];
        int y = (int) tileIndex[1];
        
        // Get or create PMTiles file for this layer and grid set
        PMTilesWriter writer = getWriter(layerName, gridSetId);
        
        // Add tile to PMTiles
        writer.addTile(z, x, y, tile.getBlob());
        
        // If this is the last tile, finalize the file
        if (isLastTile(layerName, gridSetId, z, x, y)) {
            writer.close();
        }
    }
    
    @Override
    public boolean get(TileObject tile) throws StorageException {
        // Get required information
        String layerName = tile.getLayerName();
        String gridSetId = tile.getGridSetId();
        long[] tileIndex = tile.getXYZ();
        int z = (int) tileIndex[2];
        int x = (int) tileIndex[0];
        int y = (int) tileIndex[1];
        
        // Get PMTiles file for this layer and grid set
        try (PMTilesReader reader = getReader(layerName, gridSetId)) {
            // Read tile from PMTiles
            Optional<byte[]> tileData = reader.getTile(z, x, y);
            
            if (tileData.isPresent()) {
                tile.setBlob(tileData.get());
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new StorageException("Failed to read tile", e);
        }
    }
    
    // Other BlobStore methods
}
```

## 7. Use Cases

### 7.1. Serving National Datasets with National Projections

National mapping agencies often work with projections optimized for their territory:

```java
// Create a PMTiles file in Dutch RD projection (EPSG:28992)
CoordinateReferenceSystem dutchRD = new ProjectedCRS(
    "EPSG:28992",
    "Amersfoort / RD New",
    new Bounds(-7000, 289000, 300000, 629000),
    new RDNewTileMatrixSet()
);

PMTiles pmtiles = PMTiles.builder()
    .source(new ShapefileSource("dutch_buildings.shp"))
    .destination("dutch_buildings.pmtiles")
    .crs(dutchRD)
    .maxZoom(16)
    .build();

pmtiles.generate(progress -> {
    System.out.printf("Processing: %.1f%%\n", progress * 100);
});
```

### 7.2. Global Datasets with Multiple Projections

For global datasets, generating tiles in multiple projections:

```java
// Data source
DataSource worldData = new GeoJSONSource("world_countries.geojson");

// Generate Web Mercator PMTiles
PMTiles.builder()
    .source(worldData)
    .destination("world_webmercator.pmtiles")
    .crs(new WebMercatorCRS())
    .maxZoom(8)
    .build()
    .generate();

// Generate WGS84 PMTiles
PMTiles.builder()
    .source(worldData)
    .destination("world_wgs84.pmtiles")
    .crs(new WGS84CRS())
    .maxZoom(8)
    .build()
    .generate();

// Generate Arctic Polar Stereographic PMTiles
PMTiles.builder()
    .source(worldData)
    .destination("world_arctic.pmtiles")
    .crs(new PolarStereographicCRS("EPSG:3995", "Arctic Polar Stereographic"))
    .maxZoom(8)
    .build()
    .generate();
```

### 7.3. GeoServer Integration

Integration with GeoServer for serving PMTiles in different projections:

```java
// GeoServer configuration
PMTilesStoreInfo store = new PMTilesStoreInfo();
store.setName("population_density");
store.setPath("/data/population_density.pmtiles");

// Add layers for different projections
CRSInfo webMercator = new CRSInfo("EPSG:3857");
LayerInfo webMercatorLayer = new LayerInfo("population_density_webmercator", store, webMercator);

CRSInfo wgs84 = new CRSInfo("EPSG:4326");
LayerInfo wgs84Layer = new LayerInfo("population_density_wgs84", store, wgs84);

// Add to GeoServer catalog
catalog.add(store);
catalog.add(webMercatorLayer);
catalog.add(wgs84Layer);
```

## 8. Implementation Considerations

### 8.1. Coordinate Transformation

While the architecture is projection-independent, coordinate transformation is still needed in specific scenarios:

1. **When input data is in a different projection than the target tiles**
2. **For reprojection requests in GeoServer**
3. **For merging datasets in different projections**

The implementation should:

1. Use JTS and appropriate coordinate transformation libraries
2. Allow pluggable transformation implementations
3. Support both forward and inverse transformations
4. Handle appropriate edge cases (poles, antimeridian, etc.)

### 8.2. ID Generation

Tile IDs in PMTiles use a Hilbert curve for ordering. This approach needs to be adapted for different projections:

1. For Web Mercator, use the standard Hilbert curve calculation
2. For other projections, implement a suitable space-filling curve
3. Ensure the IDs maintain spatial locality appropriate for the projection

### 8.3. Bounds Calculation

Working with different projections requires careful bounds handling:

1. Respect the native bounds of each projection
2. Handle special cases like infinite bounds
3. Properly clip geometries at projection boundaries
4. Consider performance implications of complex bounds checks

## 9. Benefits Over Tippecanoe

This projection-independent approach offers several advantages over Tippecanoe:

1. **Native projection support**: Work with data in its native projection without forced reprojection
2. **GIS integration**: Better integration with GIS systems that use multiple projections
3. **Optimization for specific regions**: Use projections optimized for specific geographic areas
4. **Standards compliance**: Better alignment with OGC standards for tile matrix sets
5. **Flexibility**: Support for specialized applications beyond web mapping

## 10. Conclusion

By designing Tileverse with projection independence from the beginning, we create a more flexible and powerful tool that can work seamlessly with GeoServer and GeoWebCache while supporting a wide range of GIS applications.

Unlike Tippecanoe's Web Mercator-centric approach, Tileverse treats the coordinate reference system as a core configurable component, allowing users to generate and consume PMTiles in the projection that best suits their data and use case.

This approach makes Tileverse suitable not just for web mapping but for a wide range of geospatial applications, scientific visualizations, and specialized GIS workflows.