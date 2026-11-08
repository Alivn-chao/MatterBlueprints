package com.gtnhblueprints.machine;

import java.math.BigInteger;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable job pricing plus a persisted remainder for partially supplied wired ticks. */
final class HostedMilestoneCredit {
    // Every supported integer percentage divides this scale: 75% and custom rates retain exact fractions.
    static final BigInteger SCALE = scale();
    private static final BigInteger[] TIME_RATES = timeRates();
    final int durationPercent;
    private final long nativePower;
    private final long chargedPower;
    private final BigInteger energyPerTick;
    private BigInteger remainder = BigInteger.ZERO;

    HostedMilestoneCredit(int durationPercent, long nativePower, long chargedPower) {
        this.durationPercent = Math.max(1, Math.min(100, durationPercent));
        this.nativePower = Math.max(0L, nativePower);
        this.chargedPower = Math.max(1L, chargedPower);
        energyPerTick = timeRate(this.durationPercent).multiply(BigInteger.valueOf(this.nativePower));
    }

    static BigInteger timeRate(int percent) {
        return TIME_RATES[Math.max(1, Math.min(100, percent))];
    }

    private static BigInteger[] timeRates() {
        BigInteger[] rates = new BigInteger[101];
        for (int i = 1; i <= 100; i++) rates[i] = SCALE.divide(BigInteger.valueOf(i)).multiply(BigInteger.valueOf(100));
        return rates;
    }

    BigInteger paid(long amount) {
        if (amount <= 0) return BigInteger.ZERO;
        if (amount == chargedPower) return energyPerTick;
        BigInteger[] divided = energyPerTick.multiply(BigInteger.valueOf(amount)).add(remainder)
            .divideAndRemainder(BigInteger.valueOf(chargedPower));
        remainder = divided[1];
        return divided[0];
    }

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("durationPercent", durationPercent);
        tag.setLong("nativePower", nativePower);
        tag.setLong("chargedPower", chargedPower);
        tag.setByteArray("remainder", remainder.toByteArray());
        return tag;
    }

    static HostedMilestoneCredit read(NBTTagCompound tag) {
        HostedMilestoneCredit result = new HostedMilestoneCredit(tag.getInteger("durationPercent"),
            tag.getLong("nativePower"), tag.getLong("chargedPower"));
        byte[] saved = tag.getByteArray("remainder");
        if (saved.length > 0) result.remainder = new BigInteger(saved).max(BigInteger.ZERO)
            .mod(BigInteger.valueOf(result.chargedPower));
        return result;
    }

    private static BigInteger scale() {
        BigInteger value = BigInteger.ONE;
        for (int i = 2; i <= 100; i++) {
            BigInteger divisor = BigInteger.valueOf(i);
            value = value.divide(value.gcd(divisor)).multiply(divisor);
        }
        return value;
    }
}
