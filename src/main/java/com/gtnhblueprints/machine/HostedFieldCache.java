package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cache field metadata only; values and native configuration are always read live. */
final class HostedFieldCache {
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<Map<String, Field>>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private HostedFieldCache() {}

    static Field get(Class<?> type, String name) {
        Map<String, Field> fields = FIELDS.get(type);
        Field cached = fields.get(name);
        return cached != null ? cached : fields.computeIfAbsent(name, key -> find(type, key));
    }

    private static Field find(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException absent) { }
        }
        throw new IllegalStateException("Unsupported hosted machine field: " + type.getName() + "." + name);
    }
}
