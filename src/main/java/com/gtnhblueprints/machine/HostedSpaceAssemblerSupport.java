package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import com.gtnhblueprints.MatterBlueprints;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Compatibility hooks for GTNH Intergalactic's fixed-voltage Space Assembler modules. */
final class HostedSpaceAssemblerSupport {

    private static final String ASSEMBLER_CLASS =
        "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleAssembler";
    private static final ClassValue<Hooks> HOOKS = new ClassValue<Hooks>() {

        @Override
        protected Hooks computeValue(Class<?> type) {
            return new Hooks(type);
        }
    };

    private HostedSpaceAssemblerSupport() {}

    static boolean isSupportedClassName(String className) {
        return className != null && (className.equals(ASSEMBLER_CLASS) || className.startsWith(ASSEMBLER_CLASS + "$"));
    }

    static boolean isSpaceAssembler(MTEMultiBlockBase machine) {
        return machine != null && isSupportedClassName(findAssemblerClassName(machine.getClass()));
    }

    static boolean isConnected(MTEMultiBlockBase machine) {
        if (!isSpaceAssembler(machine)) return false;
        return HOOKS.get(machine.getClass()).isConnected(machine);
    }

    static int getParallelCapacity(MTEMultiBlockBase machine) {
        if (!isSpaceAssembler(machine)) return Math.max(1, machine.getTrueParallel());
        return HOOKS.get(machine.getClass()).getParallelCapacity(machine);
    }

    private static String findAssemblerClassName(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            String name = current.getName();
            if (isSupportedClassName(name)) return name;
            current = current.getSuperclass();
        }
        return "";
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static final class Hooks {

        private final Method connected;
        private final Method maximumParallels;
        private final Field parallelParameter;
        private final Field processingLogic;

        Hooks(Class<?> type) {
            connected = findMethod(type, "isConnected");
            maximumParallels = findMethod(type, "getMaxParallels");
            parallelParameter = findField(type, "parallelParameter");
            processingLogic = findField(type, "processingLogic");
        }

        boolean isConnected(MTEMultiBlockBase machine) {
            if (connected == null) return false;
            try {
                return Boolean.TRUE.equals(connected.invoke(machine));
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not read Space Assembler elevator connection", error);
                return false;
            }
        }

        int getParallelCapacity(MTEMultiBlockBase machine) {
            Integer configured = readProcessingSupplier(machine);
            if (configured == null) configured = readParallelParameter(machine);
            int maximum = readMaximumParallels(machine);
            if (configured == null) return Math.max(1, maximum);
            return Math.max(1, Math.min(maximum, configured.intValue()));
        }

        @SuppressWarnings("unchecked")
        private Integer readProcessingSupplier(MTEMultiBlockBase machine) {
            if (processingLogic == null) return null;
            try {
                Object logic = processingLogic.get(machine);
                if (logic == null) return null;
                Field supplierField = findField(logic.getClass(), "maxParallelSupplier");
                if (supplierField == null) return null;
                Supplier<Integer> supplier = (Supplier<Integer>) supplierField.get(logic);
                return supplier == null ? null : supplier.get();
            } catch (ReflectiveOperationException | ClassCastException error) {
                MatterBlueprints.LOG.warn("Could not read Space Assembler parallel supplier", error);
                return null;
            }
        }

        private Integer readParallelParameter(MTEMultiBlockBase machine) {
            if (parallelParameter == null) return null;
            try {
                Object parameter = parallelParameter.get(machine);
                if (parameter == null) return null;
                Object value = parameter.getClass()
                    .getMethod("getValue")
                    .invoke(parameter);
                return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not read Space Assembler parallel parameter", error);
                return null;
            }
        }

        private int readMaximumParallels(MTEMultiBlockBase machine) {
            if (maximumParallels == null) return Math.max(1, machine.getTrueParallel());
            try {
                return Math.max(1, ((Number) maximumParallels.invoke(machine)).intValue());
            } catch (ReflectiveOperationException | ClassCastException error) {
                MatterBlueprints.LOG.warn("Could not read Space Assembler maximum parallels", error);
                return Math.max(1, machine.getTrueParallel());
            }
        }
    }
}
