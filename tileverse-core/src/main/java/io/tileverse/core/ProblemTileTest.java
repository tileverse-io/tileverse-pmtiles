package io.tileverse.core;

/**
 * Specifically tests the problematic tile coordinates.
 */
public class ProblemTileTest {

    public static void main(String[] args) {
        // The problematic coordinates
        int z = 7;
        int x = 34;
        int y = 51;

        // First, create a new ZXY object
        ZXY zxy = new ZXY((byte) z, x, y);

        // Convert to tile ID
        long tileId = zxy.toTileId();

        // Display the result
        System.out.println("Original coordinates: " + z + "/" + x + "/" + y);
        System.out.println("Converted to tileId: " + tileId);

        // Convert back to ZXY
        ZXY reconverted = ZXY.fromTileId(tileId);
        System.out.println("Converted back to: " + reconverted.z() + "/" + reconverted.x() + "/" + reconverted.y());

        // Double check with the reference implementation directly
        int n = 1 << z;
        long d = HilbertCurve.xy2d(n, x, y);
        int[] coords = HilbertCurve.d2xy(n, d);

        System.out.println("\nReference implementation:");
        System.out.println("xy2d: " + d);
        System.out.println("d2xy: " + coords[0] + "/" + coords[1]);

        // Verify if the results match
        boolean forwardMatches = d == tileId - 5461; // Subtract the accumulated count before our zoom level
        boolean backwardMatches = coords[0] == reconverted.x() && coords[1] == reconverted.y();

        System.out.println("\nMatches: " + (forwardMatches && backwardMatches));
    }
}
