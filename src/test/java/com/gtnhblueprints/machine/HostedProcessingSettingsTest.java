package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import gregtech.api.logic.ProcessingLogic;

class HostedProcessingSettingsTest {
    @Test
    void dynamicMachineSpeedDoesNotCompoundAndRestoresAfterFailure() {
        InspectableLogic logic = new InspectableLogic();
        double[] nativeSpeed = { 0.5 };
        Supplier<Double> supplier = () -> nativeSpeed[0];
        logic.setSpeedBonus(0.75).setSpeedBonusSupplier(supplier).setBatchSize(16);
        assertThrows(IllegalStateException.class, () -> {
            try (HostedProcessingSettings scope = new HostedProcessingSettings(logic, 0.25, true)) {
                assertEquals(0.125, logic.evaluateSpeed());
                assertEquals(0.125, logic.evaluateSpeed());
                nativeSpeed[0] = 0.25;
                assertEquals(0.0625, logic.evaluateSpeed());
                assertEquals(16, logic.batch());
                throw new IllegalStateException("Failed recipe check");
            }
        });
        assertSame(supplier, logic.supplier());
        assertEquals(0.75, logic.speed());
        assertEquals(16, logic.batch());
        try (HostedProcessingSettings scope = new HostedProcessingSettings(logic, 0.5, false)) {
            assertEquals(1, logic.batch());
            assertEquals(0.125, logic.evaluateSpeed());
        }
        assertEquals(16, logic.batch());
    }

    @Test
    void batchDurationRoundsPartialTicksUpAndSaturates() {
        assertEquals(3, HostedProcessingSettings.duration(1, 2.5));
        assertEquals(Integer.MAX_VALUE, HostedProcessingSettings.duration(Integer.MAX_VALUE, 128));
    }

    private static final class InspectableLogic extends ProcessingLogic {
        double evaluateSpeed() { return speedBoost = speedBoostSupplier.get(); }
        double speed() { return speedBoost; }
        Supplier<Double> supplier() { return speedBoostSupplier; }
        int batch() { return batchSize; }
    }
}
