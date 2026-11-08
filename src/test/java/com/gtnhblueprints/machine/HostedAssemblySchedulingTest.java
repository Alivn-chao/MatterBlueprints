package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class HostedAssemblySchedulingTest {
    @Test
    void inactiveFlashRecipesDoNotReserveAnyOfTheFortyUnits() {
        List<Integer> flash = new ArrayList<>();
        for (int i = 0; i < 100; i++) flash.add(i);
        List<Integer> allocations = new ArrayList<>();
        assertTrue(HostedAssemblyScheduling.schedule(flash, 40, 10,
            (recipe, units) -> recipe == 47 ? units : 0,
            (recipe, units) -> { allocations.add(units); return units; }));
        assertEquals(Arrays.asList(40), allocations);
    }

    @Test
    void sharesCapacityOnlyAmongRunnableRecipes() {
        List<Integer> allocations = new ArrayList<>();
        assertTrue(HostedAssemblyScheduling.schedule(Arrays.asList(0, 1, 2, 3), 40, 10,
            (recipe, units) -> recipe % 2 == 0 ? units : 0,
            (recipe, units) -> { allocations.add(units); return units; }));
        assertEquals(Arrays.asList(20, 20), allocations);
    }

    @Test
    void scarceRecipeReturnsItsUnusedShareRegardlessOfRecipeOrder() {
        assertArrayEquals(new int[] { 39, 1 }, HostedAssemblyScheduling.shares(40, Arrays.asList(40, 1)));
        assertArrayEquals(new int[] { 1, 39 }, HostedAssemblyScheduling.shares(40, Arrays.asList(1, 40)));
        assertArrayEquals(new int[] { 2, 19, 19 }, HostedAssemblyScheduling.shares(40, Arrays.asList(2, 40, 40)));
    }

    @Test
    void severalPatternBuffersCanRunTheSameRecipeOnIdleControllers() {
        AtomicInteger buffers = new AtomicInteger(10);
        AtomicInteger consumed = new AtomicInteger();
        assertTrue(HostedAssemblyScheduling.schedule(Arrays.asList("same recipe"), 40, 10,
            (recipe, units) -> buffers.get() > 0 ? Math.min(4, units) : 0,
            (recipe, units) -> { buffers.decrementAndGet(); consumed.addAndGet(units); return units; }));
        assertEquals(0, buffers.get());
        assertEquals(40, consumed.get());
    }

    @Test
    void newlyIdleCapacityCanJoinAnAlreadyRunningRecipe() {
        // Six of the ten controllers still run older jobs reserving 24 of the 40 units.
        AtomicInteger reserved = new AtomicInteger(24);
        assertTrue(HostedAssemblyScheduling.schedule(Arrays.asList("already running recipe"), 40 - 24, 10 - 6,
            (recipe, units) -> units,
            (recipe, units) -> { reserved.addAndGet(units); return units; }));
        assertEquals(40, reserved.get());
    }

    @Test
    void changedSharedInputsAreRecheckedAndLeftoverCapacityIsRetried() {
        AtomicInteger firstStock = new AtomicInteger(40);
        AtomicInteger secondStock = new AtomicInteger(40);
        AtomicInteger committed = new AtomicInteger();
        List<Integer> starts = new ArrayList<>();
        assertTrue(HostedAssemblyScheduling.schedule(Arrays.asList(0, 1), 40, 10,
            (recipe, units) -> Math.min(units, recipe == 0 ? firstStock.get() : secondStock.get()),
            (recipe, units) -> {
                if (recipe == 1) return 0;
                int used = Math.min(units, firstStock.get());
                firstStock.addAndGet(-used);
                secondStock.set(0); // The earlier start invalidates the other recipe's simulated availability.
                starts.add(used);
                committed.addAndGet(used);
                return used;
            }));
        assertEquals(Arrays.asList(20, 20), starts);
        assertEquals(40, committed.get());
        assertEquals(0, firstStock.get());
    }

    @Test
    void taskLimitDoesNotDivideOneRecipesCapacity() {
        List<Integer> allocations = new ArrayList<>();
        HostedAssemblyScheduling.schedule(Arrays.asList("a", "b"), 40, 1,
            (recipe, units) -> units,
            (recipe, units) -> { allocations.add(units); return units; });
        assertEquals(Arrays.asList(40), allocations);
    }

    @Test
    void materialLimitedBuffersCannotExceedFreeTaskSlots() {
        AtomicInteger started = new AtomicInteger();
        HostedAssemblyScheduling.schedule(Arrays.asList("a"), 40, 2,
            (recipe, units) -> 1,
            (recipe, units) -> { started.incrementAndGet(); return 1; });
        assertEquals(2, started.get());
    }

    @Test
    void equalShareBelowMinimumPowerFallsBackToOneRunnableRecipe() {
        AtomicInteger occupied = new AtomicInteger();
        assertTrue(HostedAssemblyScheduling.schedule(Arrays.asList("a", "b"), 4, 2,
            (recipe, units) -> units >= 3 ? units : 0,
            (recipe, units) -> { if (units < 3) return 0; occupied.addAndGet(units); return units; }));
        assertEquals(4, occupied.get());
    }

    @Test
    void failedStartsTerminateWithoutConsumingUnitsOrLoopingForever() {
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(HostedAssemblyScheduling.schedule(Arrays.asList("a", "b"), 40, 10,
            (recipe, units) -> units,
            (recipe, units) -> { attempts.incrementAndGet(); return 0; }));
        assertEquals(4, attempts.get());
    }

    @Test
    void noFreeCapacityDoesNotEvenProbeInputs() {
        HostedAssemblyScheduling.Attempt<String> unexpected = (recipe, units) -> {
            fail("Must not probe or consume with no free capacity"); return 0;
        };
        assertFalse(HostedAssemblyScheduling.schedule(Arrays.asList("a"), 0, 10, unexpected, unexpected));
        assertFalse(HostedAssemblyScheduling.schedule(Arrays.asList("a"), 40, 0, unexpected, unexpected));
    }

    @Test
    void integerLimitDoesNotOverflowOrIterateOncePerUnit() {
        assertArrayEquals(new int[] { 715827883, 715827882, 715827882 },
            HostedAssemblyScheduling.shares(Integer.MAX_VALUE,
                Arrays.asList(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)));
        assertArrayEquals(new int[] { 1, 2 }, HostedAssemblyScheduling.shares(Integer.MAX_VALUE, Arrays.asList(1, 2)));
    }
}
