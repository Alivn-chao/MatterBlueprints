package com.gtnhblueprints.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;


/** Unordered allocation with shared-stock accounting, including assembly-line alternatives and their amounts. */
final class HostedAssemblyInputs {

    private HostedAssemblyInputs() {}

    static Plan plan(ItemStack[] required, ItemStack[][] alternatives, FluidStack[] requiredFluids,
        ItemStack[] items, FluidStack[] fluids, int parallel) {
        if (parallel <= 0) return null;
        long[] fluidUse = new long[fluids.length];
        for (FluidStack wanted : requiredFluids) {
            if (wanted == null) continue;
            long remaining = (long) wanted.amount * parallel;
            for (int i = 0; i < fluids.length && remaining > 0; i++) {
                if (fluids[i] == null || !wanted.isFluidEqual(fluids[i])) continue;
                long take = Math.min(remaining, Math.max(0L, fluids[i].amount - fluidUse[i]));
                fluidUse[i] += take;
                remaining -= take;
            }
            if (remaining > 0) return null;
        }
        int[][] amounts = new int[required.length][items.length];
        List<List<Integer>> choices = new ArrayList<>();
        for (int r = 0; r < required.length; r++) {
            List<Integer> quantities = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                int amount = items[i] == null || items[i].stackSize <= 0 ? -1
                    : matchedAmount(items[i], required[r],
                        alternatives != null && r < alternatives.length ? alternatives[r] : null);
                amounts[r][i] = amount;
                if (amount >= 0 && !quantities.contains(amount)) quantities.add(amount);
            }
            if (quantities.isEmpty()) return null;
            choices.add(quantities);
        }
        // A native AL slot selects one alternative amount. Equal-amount alternatives can share multiple stacks.
        long[] itemUse = chooseAmounts(0, new int[required.length], choices, amounts, items, parallel, new int[] { 4096 });
        return itemUse == null ? null : new Plan(itemUse, fluidUse);
    }

    // Same item/meta/wildcard and alternative-amount semantics as RecipeAssemblyLine.getMatchedIngredientAmount.
    // Assembly-line matching intentionally ignores NBT, as does the native ordered input implementation.
    private static int matchedAmount(ItemStack available, ItemStack required, ItemStack[] alternatives) {
        if (alternatives != null && alternatives.length > 0) {
            for (ItemStack alternative : alternatives) if (matches(available, alternative)) return alternative.stackSize;
            return -1;
        }
        return matches(available, required) ? required.stackSize : -1;
    }

    private static boolean matches(ItemStack left, ItemStack right) {
        return right != null && left.getItem() == right.getItem()
            && (left.getItemDamage() == right.getItemDamage() || left.getItemDamage() == 32767 || right.getItemDamage() == 32767);
    }

    private static long[] chooseAmounts(int row, int[] selected, List<List<Integer>> choices, int[][] amounts,
        ItemStack[] items, int parallel, int[] budget) {
        if (budget[0]-- <= 0) return null;
        if (row == selected.length) return allocate(selected, amounts, items, parallel);
        for (int amount : choices.get(row)) {
            selected[row] = amount;
            long[] result = chooseAmounts(row + 1, selected, choices, amounts, items, parallel, budget);
            if (result != null) return result;
        }
        return null;
    }

    private static long[] allocate(int[] selected, int[][] amounts, ItemStack[] items, int parallel) {
        int sink = 1 + items.length + selected.length;
        List<List<Edge>> graph = new ArrayList<>();
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        Edge[] supplies = new Edge[items.length];
        for (int i = 0; i < items.length; i++) {
            supplies[i] = edge(graph, 0, 1 + i, items[i] == null ? 0 : Math.max(0, items[i].stackSize));
        }
        long needed = 0;
        for (int r = 0; r < selected.length; r++) {
            long demand = (long) selected[r] * parallel;
            if (needed > Long.MAX_VALUE - demand) return null;
            needed += demand;
            edge(graph, 1 + items.length + r, sink, demand);
            for (int i = 0; i < items.length; i++) {
                if (amounts[r][i] == selected[r]) edge(graph, 1 + i, 1 + items.length + r, demand);
            }
        }
        while (needed > 0) {
            long flow = augment(graph, 0, sink, needed, new boolean[sink + 1]);
            if (flow == 0) return null;
            needed -= flow;
        }
        long[] used = new long[items.length];
        for (int i = 0; i < used.length; i++) {
            used[i] = (items[i] == null ? 0 : Math.max(0, items[i].stackSize)) - supplies[i].capacity;
        }
        return used;
    }

    private static Edge edge(List<List<Edge>> graph, int from, int to, long capacity) {
        Edge forward = new Edge(to, capacity);
        Edge reverse = new Edge(from, 0);
        forward.reverse = reverse;
        reverse.reverse = forward;
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
        return forward;
    }

    private static long augment(List<List<Edge>> graph, int node, int sink, long limit, boolean[] seen) {
        if (node == sink) return limit;
        seen[node] = true;
        for (Edge edge : graph.get(node)) {
            if (edge.capacity <= 0 || seen[edge.to]) continue;
            long flow = augment(graph, edge.to, sink, Math.min(limit, edge.capacity), seen);
            if (flow <= 0) continue;
            edge.capacity -= flow;
            edge.reverse.capacity += flow;
            return flow;
        }
        return 0;
    }

    private static final class Edge {
        final int to;
        long capacity;
        Edge reverse;

        Edge(int to, long capacity) {
            this.to = to;
            this.capacity = capacity;
        }
    }

    static final class Plan {
        final long[] items;
        final long[] fluids;

        Plan(long[] items, long[] fluids) {
            this.items = items;
            this.fluids = fluids;
        }

        void consume(ItemStack[] stacks, FluidStack[] tanks) {
            for (int i = 0; i < items.length; i++) if (stacks[i] != null) stacks[i].stackSize -= (int) items[i];
            for (int i = 0; i < fluids.length; i++) if (tanks[i] != null) tanks[i].amount -= (int) fluids[i];
        }
    }
}
