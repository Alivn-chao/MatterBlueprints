package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import gregtech.api.logic.ProcessingLogic;

/** Applies host speed before GT converts sub-tick duration into parallel work. */
final class HostedProcessingSettings implements AutoCloseable {
    static final int BATCH_SIZE = 128;
    private static final Field SPEED = field("speedBoost");
    private static final Field SPEED_SUPPLIER = field("speedBoostSupplier");
    private static final Field BATCH = field("batchSize");

    private final ProcessingLogic logic;
    private final double speed;
    private final Supplier<Double> speedSupplier;
    private final int batchSize;

    @SuppressWarnings("unchecked")
    HostedProcessingSettings(ProcessingLogic logic, double durationMultiplier, boolean batch) {
        this.logic = logic;
        try {
            speed = SPEED.getDouble(logic);
            speedSupplier = (Supplier<Double>) SPEED_SUPPLIER.get(logic);
            batchSize = BATCH.getInt(logic);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read hosted speed/batch settings", error);
        }
        logic.setSpeedBonusSupplier(() -> (speedSupplier == null ? speed : speedSupplier.get()) * durationMultiplier);
        logic.setBatchSize(batch ? (batchSize > 1 ? batchSize : BATCH_SIZE) : 1);
    }

    @Override
    public void close() {
        logic.setSpeedBonusSupplier(speedSupplier);
        logic.setSpeedBonus(speed);
        logic.setBatchSize(batchSize);
    }

    static int duration(int ticks, double batchMultiplier) {
        return (int) Math.max(1D, Math.min(Integer.MAX_VALUE, Math.ceil(ticks * batchMultiplier)));
    }

    private static Field field(String name) {
        try {
            Field result = ProcessingLogic.class.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported processing setting: " + name, error);
        }
    }
}
