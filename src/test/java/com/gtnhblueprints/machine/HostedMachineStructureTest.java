package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HostedMachineStructureTest {

    @Test
    void machineRoomShapeHasExpectedDimensionsAndParts() {
        String[][] layers = HostedMachineStructure.LAYERS;
        assertEquals(6, layers.length);
        for (String[] layer : layers) {
            assertEquals(7, layer.length);
            for (String row : layer) assertEquals(9, row.length());
        }

        assertEquals(63, count(layers, 'F'));
        assertEquals(71, count(layers, 'C'));
        assertEquals(13, count(layers, 'P'));
        assertEquals(36, count(layers, 'L'));
        assertEquals(1, count(layers, 'R'));
        assertEquals(1, count(layers, '~'));
        assertEquals('~', layers[1][0].charAt(4));
        assertEquals('R', layers[5][3].charAt(4));
    }

    private static int count(String[][] layers, char expected) {
        int result = 0;
        for (String[] layer : layers) {
            for (String row : layer) {
                for (int index = 0; index < row.length(); index++) {
                    if (row.charAt(index) == expected) result++;
                }
            }
        }
        return result;
    }
}
