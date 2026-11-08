package com.gtnhblueprints.machine;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;

/** Live read-only traversal; no per-query copy of the host's task lists. */
final class HostedCombinedView<T, U> implements Iterable<T> {
    private final Iterable<T> primary;
    private final Iterable<U> extra;
    private final Predicate<U> included;
    private final Function<U, T> value;

    HostedCombinedView(Iterable<T> primary, Iterable<U> extra, Predicate<U> included, Function<U, T> value) {
        this.primary = primary; this.extra = extra; this.included = included; this.value = value;
    }

    @Override public Iterator<T> iterator() {
        return new Iterator<T>() {
            private final Iterator<T> first = primary.iterator();
            private Iterator<U> second;
            private T next;
            private boolean ready;

            @Override public boolean hasNext() {
                if (ready) return true;
                if (first.hasNext()) {
                    next = first.next(); ready = true; return true;
                }
                if (second == null) second = extra.iterator();
                while (second.hasNext()) {
                    U candidate = second.next();
                    if (included.test(candidate)) {
                        next = value.apply(candidate); ready = true; return true;
                    }
                }
                return false;
            }

            @Override public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                T result = next; next = null; ready = false; return result;
            }
        };
    }
}
