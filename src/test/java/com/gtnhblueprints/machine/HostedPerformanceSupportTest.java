package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HostedPerformanceSupportTest {
    static class Parent { private int value = 3; }
    static class Child extends Parent { private long live = 9; }
    static class Shadow extends Parent { private int value = 5; }

    @Test
    void cachedMetadataStillReadsLivePerMachineValuesAndSubclassShadowing() throws Exception {
        Field field = HostedFieldCache.get(Child.class, "value");
        assertSame(field, HostedFieldCache.get(Child.class, "value"));
        Child first = new Child(), second = new Child();
        field.setInt(first, 100);
        assertEquals(100, HostedFieldCache.get(Child.class, "value").getInt(first));
        assertEquals(3, field.getInt(second));
        assertEquals(5, HostedFieldCache.get(Shadow.class, "value").getInt(new Shadow()));
        assertEquals(9L, HostedFieldCache.get(Child.class, "live").getLong(first));
        assertThrows(IllegalStateException.class, () -> HostedFieldCache.get(Child.class, "missing"));
    }

    @Test
    void taskViewMatchesSnapshotAcross128MachinesAndBindingChanges() {
        List<Integer> primary = new ArrayList<>(), extra = new ArrayList<>();
        Set<Integer> bound = new HashSet<>();
        for (int i = 0; i < 128; i++) { primary.add(i); extra.add(i + 128); bound.add(i + 128); }
        HostedCombinedView<Integer, Integer> view = new HostedCombinedView<>(primary, extra, bound::contains, value -> value);
        List<Integer> expected = new ArrayList<>(primary); expected.addAll(extra);
        assertEquals(expected, collect(view));
        bound.remove(200); primary.set(0, -10); extra.add(999);
        expected = new ArrayList<>(primary);
        for (int task : extra) if (bound.contains(task)) expected.add(task);
        assertEquals(expected, collect(view));
        primary.clear(); extra.clear();
        assertTrue(collect(view).isEmpty());
        primary.add(42); extra.add(200); bound.add(200);
        assertEquals(Arrays.asList(42, 200), collect(view));
    }

    @Test
    void independentIteratorsDoNotSkipTasksOnRepeatedHasNext() {
        HostedCombinedView<Integer, Integer> view = new HostedCombinedView<>(Arrays.asList(1, 2),
            Arrays.asList(3, 4, 5), value -> value != 4, value -> value * 10);
        Iterator<Integer> first = view.iterator(), second = view.iterator();
        assertTrue(first.hasNext()); assertTrue(first.hasNext());
        assertEquals(1, first.next()); assertEquals(1, second.next());
        assertEquals(2, first.next()); assertEquals(30, first.next()); assertEquals(50, first.next());
        assertFalse(first.hasNext()); assertFalse(first.hasNext());
        assertThrows(NoSuchElementException.class, first::next);
        assertThrows(UnsupportedOperationException.class, second::remove);
        assertEquals(2, second.next());
    }

    private static <T> List<T> collect(Iterable<T> view) {
        List<T> result = new ArrayList<>();
        for (T value : view) result.add(value);
        return result;
    }
}
