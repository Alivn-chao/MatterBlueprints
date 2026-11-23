package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HostedFinalFormTest {

    @BeforeAll
    static void initializeMinecraft() throws Exception {
        AssemblyTestBootstrap.initialize();
    }

    @Test
    void sharedVirtualCircuitSurvivesInputFiltering() {
        Item circuit = new Item();
        ItemStack virtual = new ItemStack(circuit, 0, 20);
        ItemStack physical = new ItemStack(circuit, 1, 21);
        ItemStack emptyMaterial = new ItemStack(new Item(), 0);
        ItemStack[] merged = HostedFinalForm.patternItems(
            new ItemStack[] { virtual }, new ItemStack[] { physical, emptyMaterial, virtual }, circuit);
        assertArrayEquals(new ItemStack[] { virtual, physical }, merged);
        assertEquals(0, virtual.stackSize);
    }

    @Test
    void unoverclockedCostUsesRecipeDurationAndUnlockedPowerDiscount() {
        // 61,440 EU/t for 1,000 ticks with the 50% milestone discount.
        BigInteger scaled = BigInteger.valueOf(61_440L * 1_000 * 50);
        assertEquals(BigInteger.valueOf(122_880_000), HostedFinalForm.recipeCost(scaled, 4));
        assertEquals(4, HostedFinalForm.affordableParallels(BigInteger.valueOf(122_880_000), scaled));
        assertEquals(3, HostedFinalForm.affordableParallels(BigInteger.valueOf(122_879_999), scaled));
    }

    @Test
    void costAndAdmissionStayExactAtIntParallelAndBeyondLongEnergy() {
        BigInteger scaled = BigInteger.valueOf((long) Integer.MAX_VALUE * Integer.MAX_VALUE).multiply(BigInteger.valueOf(25));
        BigInteger cost = HostedFinalForm.recipeCost(scaled, Integer.MAX_VALUE);
        assertEquals(new BigInteger("2475880075111996037588320256"), cost);
        assertEquals(Integer.MAX_VALUE, HostedFinalForm.affordableParallels(cost, scaled));
        assertEquals(Integer.MAX_VALUE - 1, HostedFinalForm.affordableParallels(cost.subtract(BigInteger.ONE), scaled));
        assertEquals(Integer.MAX_VALUE, HostedFinalForm.affordableParallels(cost.shiftLeft(4), scaled));
    }

    @Test
    void fractionalEnergyRoundsUpOnlyAfterMultiplyingTheBatch() {
        BigInteger scaled = BigInteger.valueOf(75);
        assertEquals(BigInteger.valueOf(3), HostedFinalForm.recipeCost(scaled, 3));
        assertEquals(BigInteger.valueOf(3), HostedFinalForm.recipeCost(scaled, 4));
        assertEquals(4, HostedFinalForm.affordableParallels(BigInteger.valueOf(3), scaled));
        assertEquals(0, HostedFinalForm.affordableParallels(BigInteger.ZERO, scaled));
    }

    @Test
    void milestonesCreditUnreducedRecipeDurationAndEnergy() {
        HostedProgression progression = new HostedProgression();
        progression.recordInstantRecipes(1_000, 61_440, 4);
        assertEquals(1_000, progression.getProductiveTicks());
        assertEquals(4, progression.getWorkUnits());
        assertEquals(BigInteger.valueOf(245_760_000), progression.getEnergyThroughput());
    }
}
