package com.gtnhblueprints.machine;

import java.util.function.LongSupplier;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Maps the reactor's per-second auxiliary fuel charge onto the extended hosted recipe clock. */
final class HostedNaquadahSupport {
    private static final ClassValue<java.lang.reflect.Field> OUTPUT = new ClassValue<java.lang.reflect.Field>() {
        @Override protected java.lang.reflect.Field computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = current.getDeclaredField("trueOutput");
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) { }
            }
            throw new IllegalStateException("Unsupported naquadah output field");
        }
    };
    private HostedNaquadahSupport() {}

    static long charge(MTEMultiBlockBase remote, LongSupplier output) {
        int progress = remote.mProgresstime;
        remote.mProgresstime = 0;
        try {
            return remote.onRunningTick(remote.getControllerSlot()) && remote.mMaxProgresstime > 0
                ? output.getAsLong() : 0L;
        } finally { remote.mProgresstime = progress; }
    }

    static boolean isReactor(MTEMultiBlockBase machine) {
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("gregtech.common.tileentities.machines.multi.MTELargeNaquadahReactor")) return true;
        }
        return false;
    }

    static int auxiliaryCycle(int progress, int baseDuration, int extendedDuration) {
        if (baseDuration <= 0 || extendedDuration <= 0) return Math.max(0, progress) / 20;
        return (int) ((long) Math.max(0, progress) * Math.min(baseDuration, extendedDuration) / extendedDuration / 20);
    }

    static boolean run(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, LongSupplier output) {
        if (!isReactor(remote) || job.generatorBaseDuration <= 0) return remote.onRunningTick(remote.getControllerSlot());
        int progress = remote.mProgresstime;
        int cycle = auxiliaryCycle(progress, job.generatorBaseDuration, remote.mMaxProgresstime);
        // GT does not persist trueOutput. Restore the paid auxiliary period after a chunk/server reload.
        if (cycle <= job.generatorAuxiliaryCycle && output.getAsLong() <= 0 && job.generatorAuxiliaryOutput > 0) {
            try {
                OUTPUT.get(remote.getClass()).setLong(remote, job.generatorAuxiliaryOutput);
            } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        boolean charge = cycle > job.generatorAuxiliaryCycle || output.getAsLong() <= 0;
        // The native reactor charges space/time/liquid air when progress % 20 == 0. Keep its native
        // fuel choice and output calculation, but fire that branch only once per extended fuel period.
        remote.mProgresstime = charge ? 0 : 1;
        try {
            boolean continued = remote.onRunningTick(remote.getControllerSlot());
            if (continued && output.getAsLong() > 0) {
                job.generatorAuxiliaryCycle = cycle;
                job.generatorAuxiliaryOutput = output.getAsLong();
            }
            return continued;
        } finally {
            remote.mProgresstime = progress;
        }
    }
}
