/*
 * (c) Copyright 2025 Multiversio LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tileverse.pmtiles;

/**
 * Validates the revised Hilbert curve implementation in the ZXY class
 * by testing round-trip conversion from coordinates to tileId and back.
 */
public class HilbertValidation {

    public static void main(String[] args) {
        System.out.println("Testing round-trip conversion for Z/X/Y coordinates:");
        System.out.println("Z/X/Y\t\tTileId\t\tConverted Back\tMatch?");
        System.out.println("--------------------------------------------------------");

        // Test a variety of coordinates, including the problematic one
        int[][] testCoords = {
            {0, 0, 0},
            {1, 0, 0},
            {1, 0, 1},
            {1, 1, 0},
            {1, 1, 1},
            {5, 16, 16},
            {7, 34, 51}, // The specific problematic case
            {7, 64, 64},
            {10, 512, 512},
            {12, 2048, 2048}
        };

        for (int[] coord : testCoords) {
            int z = coord[0];
            int x = coord[1];
            int y = coord[2];

            // Create ZXY from coordinates
            ZXY original = new ZXY((byte) z, x, y);

            // Convert to tileId
            long tileId = original.toTileId();

            // Convert back to ZXY
            ZXY converted = ZXY.fromTileId(tileId);

            // Check if round-trip conversion is successful
            boolean matches =
                    (converted.z() == original.z() && converted.x() == original.x() && converted.y() == original.y());

            System.out.printf(
                    "%d/%d/%d\t\t%d\t\t%d/%d/%d\t%s%n",
                    z, x, y, tileId, converted.z(), converted.x(), converted.y(), matches ? "✓" : "✗");
        }
    }
}
