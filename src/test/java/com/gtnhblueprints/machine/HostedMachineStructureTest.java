package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HostedMachineStructureTest {

    @Test
    void machineRoomShapeHasExpectedDimensionsAndParts() {
        String[][] layers = HostedMachineStructure.LAYERS;
        assertEquals(HostedMachineStructure.HEIGHT, layers.length);
        for (String[] layer : layers) {
            assertEquals(HostedMachineStructure.DEPTH, layer.length);
            for (String row : layer) assertEquals(HostedMachineStructure.WIDTH, row.length());
        }

        assertEquals(63, count(layers, 'F'));
        assertEquals(71, count(layers, 'C'));
        assertEquals(13, count(layers, 'P'));
        assertEquals(36, count(layers, 'L'));
        assertEquals(1, count(layers, 'R'));
        assertEquals(1, count(layers, '~'));
        assertEquals(
            '~',
            layers[HostedMachineStructure.CONTROLLER_Y][HostedMachineStructure.CONTROLLER_Z]
                .charAt(HostedMachineStructure.CONTROLLER_X));
        assertEquals('R', layers[0][3].charAt(4));
        assertEquals('F', layers[HostedMachineStructure.HEIGHT - 1][0].charAt(0));
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
