package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WirelessEnergyBufferTest {

    @Test
    void generationDepositsPositiveEnergyIntoOnlyTheSelectedTeam() {
        Grid grid = new Grid(0);
        assertTrue(WirelessEnergyBuffer.deposit(FIRST, 8192, grid));
        assertEquals(BigInteger.valueOf(8192), grid.balances.get(FIRST));
        assertEquals(BigInteger.ZERO, grid.balances.get(SECOND));
        assertTrue(WirelessEnergyBuffer.deposit(FIRST, Long.MAX_VALUE, grid));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(8192)), grid.balances.get(FIRST));
    }

    @Test
    void invalidGenerationDoesNotTouchLedgerAndRejectionIsReported() {
        Grid grid = new Grid(0);
        assertFalse(WirelessEnergyBuffer.deposit(null, 100, grid));
        assertFalse(WirelessEnergyBuffer.deposit(FIRST, -1, grid));
        assertTrue(WirelessEnergyBuffer.deposit(FIRST, 0, grid));
        assertEquals(0, grid.calls);
        assertFalse(WirelessEnergyBuffer.deposit(FIRST, 100, (account, delta) -> false));
    }

    @Test
    void generationDoesNotBecomeRefundablePrepaidEnergy() {
        Grid grid = new Grid(1000);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        assertTrue(buffer.consume(FIRST, 10, 20, grid));
        assertTrue(WirelessEnergyBuffer.deposit(FIRST, 500, grid));
        assertTrue(buffer.refund(grid));
        assertEquals(BigInteger.valueOf(1490), grid.balances.get(FIRST));
        assertTrue(buffer.refund(grid));
        assertEquals(BigInteger.valueOf(1490), grid.balances.get(FIRST));
    }

    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);

    private static final class Grid implements WirelessEnergyBuffer.Ledger {
        final Map<UUID, BigInteger> balances = new HashMap<UUID, BigInteger>();
        int calls;
        boolean rejectRefund;

        Grid(long balance) {
            balances.put(FIRST, BigInteger.valueOf(balance));
            balances.put(SECOND, BigInteger.valueOf(balance));
        }

        @Override
        public boolean change(UUID account, long delta) {
            calls++;
            BigInteger next = balances.get(account).add(BigInteger.valueOf(delta));
            if (next.signum() < 0 || (delta > 0 && rejectRefund)) return false;
            balances.put(account, next);
            return true;
        }
    }

    @Test
    void twentyTicksOfAggregatedPowerUseOneWithdrawal() {
        Grid grid = new Grid(10000);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        for (int i = 0; i < 20; i++) assertTrue(buffer.consume(FIRST, 100, 20, grid));
        assertEquals(1, grid.calls);
        assertEquals(BigInteger.valueOf(8000), grid.balances.get(FIRST));
        assertEquals(0, buffer.getStored());
    }

    @Test
    void lowBalanceCanStillPayForOneTickWithoutOverdraft() {
        Grid grid = new Grid(150);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        assertTrue(buffer.consume(FIRST, 100, 20, grid));
        assertEquals(BigInteger.valueOf(50), grid.balances.get(FIRST));
        assertFalse(buffer.consume(FIRST, 100, 20, grid));
        assertEquals(BigInteger.valueOf(50), grid.balances.get(FIRST));
        assertEquals(0, buffer.getStored());
    }

    @Test
    void failedWithdrawalDoesNotConsumePartialBuffer() {
        Grid grid = new Grid(0);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        buffer.restore(FIRST, 50);
        assertFalse(buffer.consume(FIRST, 100, 20, grid));
        assertEquals(50, buffer.getStored());
    }

    @Test
    void changingAccountRefundsOldAccountBeforeChargingNewAccount() {
        Grid grid = new Grid(10000);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        assertTrue(buffer.consume(FIRST, 100, 20, grid));
        assertTrue(buffer.consume(SECOND, 100, 1, grid));
        assertEquals(BigInteger.valueOf(9900), grid.balances.get(FIRST));
        assertEquals(BigInteger.valueOf(9900), grid.balances.get(SECOND));
        assertEquals(SECOND, buffer.getAccount());
    }

    @Test
    void failedRefundCannotCreditAnotherAccountOrForgetEnergy() {
        Grid grid = new Grid(10000);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        assertTrue(buffer.consume(FIRST, 100, 20, grid));
        grid.rejectRefund = true;
        assertFalse(buffer.consume(SECOND, 100, 20, grid));
        assertEquals(1900, buffer.getStored());
        assertEquals(FIRST, buffer.getAccount());
        assertEquals(BigInteger.valueOf(10000), grid.balances.get(SECOND));
    }

    @Test
    void savedPrepaymentIsUsedAfterReloadWithoutAnotherWithdrawal() {
        Grid grid = new Grid(10000);
        WirelessEnergyBuffer original = new WirelessEnergyBuffer();
        assertTrue(original.consume(FIRST, 100, 20, grid));
        WirelessEnergyBuffer restored = new WirelessEnergyBuffer();
        restored.restore(original.getAccount(), original.getStored());
        assertTrue(restored.consume(FIRST, 100, 20, grid));
        assertEquals(1, grid.calls);
        assertTrue(restored.refund(grid));
        assertEquals(BigInteger.valueOf(9800), grid.balances.get(FIRST));
        assertTrue(restored.refund(grid));
        assertEquals(2, grid.calls);
        assertNull(restored.getAccount());
    }

    @Test
    void changingPowerDrawStillBalancesExactlyAfterRefund() {
        Grid grid = new Grid(100000);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        long used = 0;
        for (long amount : new long[] { 100, 250, 25, 3000, 13, 500 }) {
            assertTrue(buffer.consume(FIRST, amount, 20, grid));
            used += amount;
        }
        assertTrue(buffer.refund(grid));
        assertEquals(BigInteger.valueOf(100000 - used), grid.balances.get(FIRST));
    }

    @Test
    void maximumLongDrawDoesNotOverflowBatchReservation() {
        Grid grid = new Grid(Long.MAX_VALUE);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        assertTrue(buffer.consume(FIRST, Long.MAX_VALUE, 20, grid));
        assertEquals(BigInteger.ZERO, grid.balances.get(FIRST));
        assertEquals(0, buffer.getStored());
    }

    @Test
    void invalidAccountAndNegativeAmountsFailClosed() {
        Grid grid = new Grid(10000);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        assertFalse(buffer.consume(null, 100, 20, grid));
        assertFalse(buffer.consume(FIRST, -1, 20, grid));
        assertTrue(buffer.consume(FIRST, 0, 20, grid));
        assertEquals(0, grid.calls);
        assertThrows(IllegalArgumentException.class, () -> buffer.restore(null, 1));
        assertThrows(IllegalArgumentException.class, () -> buffer.restore(FIRST, -1));
    }

    @Test
    void shortRemainingTaskDoesNotReserveTwentyTicks() {
        Grid grid = new Grid(10000);
        WirelessEnergyBuffer buffer = new WirelessEnergyBuffer();
        assertTrue(buffer.consume(FIRST, 100, 2, grid));
        assertEquals(100, buffer.getStored());
        assertEquals(BigInteger.valueOf(9800), grid.balances.get(FIRST));
    }
}
