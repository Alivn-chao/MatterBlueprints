package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class HostedNumberFormatterTest {

    @Test
    void convertsPowerToEquivalentAmpsWithoutOverflow() {
        assertEquals("1024", HostedNumberFormatter.amperage(8_589_934_592L, 8_388_608L));
        assertEquals("1.5", HostedNumberFormatter.amperage(48, 32));
        assertEquals("18.3", HostedNumberFormatter.amperage(39_300_000_000L, 2_147_483_640L));
        assertEquals("1099511627776", HostedNumberFormatter.amperage(Long.MAX_VALUE, 8_388_608L));
        assertEquals("0", HostedNumberFormatter.amperage(0, 32));
        assertEquals("—", HostedNumberFormatter.amperage(1, 0));
    }

    @Test
    void formatsLargeValuesAsShortScientificNotation() {
        assertEquals("7.75×10^8", HostedNumberFormatter.format(775_065_600L));
        assertEquals("1×10^19", HostedNumberFormatter.format(BigInteger.TEN.pow(19)));
        assertEquals("-1.79×10^11", HostedNumberFormatter.format(-178_575_121_336L));
    }

    @Test
    void leavesSmallValuesUnchanged() {
        assertEquals("999999", HostedNumberFormatter.format(999_999L));
        assertEquals("29", HostedNumberFormatter.format(29L));
    }

    @Test
    void compactsCommaSeparatedAndPlainInheritedTooltipValues() {
        assertEquals(
            "晶圆 × 7.75×10^8 (1.79×10^11 EU/t)",
            HostedNumberFormatter.compactLargeIntegers("晶圆 × 775,065,600 (178575121336 EU/t)"));
    }
}
