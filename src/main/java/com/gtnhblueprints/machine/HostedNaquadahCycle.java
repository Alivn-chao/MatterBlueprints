package com.gtnhblueprints.machine;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

/** Paid recipe energy, independent of the physical output rate. */
final class HostedNaquadahCycle {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    final int baseTicks;
    final int boostedTicks;
    int settledTicks;
    BigInteger energy = BigInteger.ZERO;
    boolean configured;
    boolean wireless;
    int wirelessTicks;

    HostedNaquadahCycle(int baseTicks, int boostedTicks, int settledTicks) {
        this.baseTicks = Math.max(1, baseTicks);
        this.boostedTicks = Math.max(1, boostedTicks);
        this.settledTicks = Math.max(0, Math.min(settledTicks, this.boostedTicks));
    }

    int auxiliaryIndex() {
        return (int) ((long) settledTicks * baseTicks / boostedTicks / 20);
    }

    void settle(long nativeOutput) {
        if (nativeOutput <= 0 || ready()) return;
        long boundary = ((long) (auxiliaryIndex() + 1) * 20 * boostedTicks + baseTicks - 1) / baseTicks;
        int end = (int) Math.min(boostedTicks, boundary);
        energy = energy.add(BigInteger.valueOf(nativeOutput).multiply(BigInteger.valueOf(end - settledTicks)));
        settledTicks = end;
    }

    boolean ready() { return settledTicks >= boostedTicks; }
    boolean finished() { return ready() && energy.signum() == 0 && (!configured || !wireless || wirelessTicks == 0); }

    void configure(boolean wireless) {
        if (!configured || this.wireless != wireless) {
            this.wireless = wireless;
            wirelessTicks = wireless ? 1200 : 0;
            configured = true;
        }
    }

    BigInteger nextOutput(long capacity) {
        if (!ready() || energy.signum() <= 0) return BigInteger.ZERO;
        if (wireless) {
            BigInteger ticks = BigInteger.valueOf(Math.max(1, wirelessTicks));
            return energy.divide(ticks);
        }
        return energy.min(BigInteger.valueOf(Math.max(0L, capacity)));
    }

    void accept(BigInteger accepted) {
        if (accepted.signum() < 0 || (!wireless && accepted.signum() == 0) || accepted.compareTo(energy) > 0) {
            throw new IllegalArgumentException("Invalid generator credit");
        }
        energy = energy.subtract(accepted);
        if (wireless && wirelessTicks > 0) wirelessTicks--;
    }

    int remainingTicks(long capacity) {
        if (wireless) return Math.max(0, wirelessTicks);
        if (capacity <= 0) return Integer.MAX_VALUE;
        BigInteger rate = BigInteger.valueOf(capacity);
        return energy.add(rate).subtract(BigInteger.ONE).divide(rate).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    static long displayPower(BigInteger value) { return value.min(LONG_MAX).longValue(); }

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("base", baseTicks);
        tag.setInteger("boosted", boostedTicks);
        tag.setInteger("settled", settledTicks);
        tag.setString("energy", energy.toString());
        tag.setBoolean("configured", configured);
        tag.setBoolean("wireless", wireless);
        tag.setInteger("wirelessTicks", wirelessTicks);
        return tag;
    }

    static HostedNaquadahCycle read(NBTTagCompound tag) {
        HostedNaquadahCycle result = new HostedNaquadahCycle(tag.getInteger("base"), tag.getInteger("boosted"), tag.getInteger("settled"));
        result.energy = new BigInteger(tag.getString("energy")).max(BigInteger.ZERO);
        result.configured = tag.getBoolean("configured");
        result.wireless = tag.getBoolean("wireless");
        result.wirelessTicks = Math.max(0, Math.min(1200, tag.getInteger("wirelessTicks")));
        return result;
    }
}
