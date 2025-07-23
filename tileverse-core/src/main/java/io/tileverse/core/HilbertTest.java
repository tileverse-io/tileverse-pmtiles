package io.tileverse.core;

/**
 * Test class to compare different Hilbert curve implementations.
 */
public class HilbertTest {

    /**
     * Current implementation in ZXY.toTileId()
     */
    public static long currentImplementation(int z, int x, int y) {
        // Accumulate the total number of tiles in all zoom levels before this one
        long acc = 0;
        for (byte t_z = 0; t_z < z; t_z++) {
            long tilesPerZoom = 1L << t_z;
            acc += tilesPerZoom * tilesPerZoom;
        }

        // Convert x,y position to a position on the Hilbert curve
        long n = 1L << z;
        long tx = x;
        long ty = y;
        long d = 0;

        for (long s = n / 2; s > 0; s /= 2) {
            long rx = (tx & s) > 0 ? 1 : 0;
            long ry = (ty & s) > 0 ? 1 : 0;
            d += s * s * ((3 * rx) ^ ry);

            // Rotate
            if (ry == 0) {
                if (rx == 1) {
                    tx = n - 1 - tx;
                    ty = n - 1 - ty;
                }
                // Swap tx and ty
                long t = tx;
                tx = ty;
                ty = t;
            }
        }

        return acc + d;
    }

    /**
     * Reference implementation that matches the JavaScript version
     */
    public static long referenceImplementation(int z, int x, int y) {
        // Accumulate the total number of tiles in all zoom levels before this one
        long acc = 0;
        for (byte t_z = 0; t_z < z; t_z++) {
            long tilesPerZoom = 1L << t_z;
            acc += tilesPerZoom * tilesPerZoom;
        }

        // This is the part that differs - we don't use a separate rotate method
        // and we're using a different mask calculation
        long h = 0;
        int tx = x;
        int ty = y;

        for (int i = 0; i < z; i++) {
            int mask = 1 << (z - i - 1); // This is different from our implementation
            int rx = (tx & mask) > 0 ? 1 : 0;
            int ry = (ty & mask) > 0 ? 1 : 0;
            h += mask * mask * ((3 * rx) ^ ry);

            if (ry == 0) {
                if (rx == 1) {
                    tx = (1 << z) - 1 - tx;
                    ty = (1 << z) - 1 - ty;
                }
                // Swap tx and ty
                int t = tx;
                tx = ty;
                ty = t;
            }
        }

        return acc + h;
    }

    /**
     * Compare both implementations for various tile coordinates
     */
    public static void main(String[] args) {
        // Test coordinates
        int[][] testCoords = {
            {0, 0, 0},
            {1, 0, 0},
            {1, 1, 0},
            {1, 0, 1},
            {1, 1, 1},
            {7, 34, 51}, // The problem coordinates
            {7, 64, 64},
            {10, 512, 512},
            {14, 8000, 8000}
        };

        System.out.println("Comparing Hilbert implementations:");
        System.out.println("z/x/y\t\tCurrent\t\tReference\tMatch?");
        System.out.println("---------------------------------------------------");

        for (int[] coord : testCoords) {
            int z = coord[0];
            int x = coord[1];
            int y = coord[2];

            long currentId = currentImplementation(z, x, y);
            long referenceId = referenceImplementation(z, x, y);
            boolean matches = currentId == referenceId;

            System.out.printf("%d/%d/%d\t\t%d\t%d\t%s%n", z, x, y, currentId, referenceId, matches ? "✓" : "✗");
        }
    }
}
