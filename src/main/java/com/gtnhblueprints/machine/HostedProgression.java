package com.gtnhblueprints.machine;

import java.math.BigInteger;
import com.gtnhblueprints.HostedMilestoneConfig;

import net.minecraft.nbt.NBTTagCompound;

/** Persistent host proficiency. Statistics only advance when useful work actually progresses. */
final class HostedProgression {

    private boolean plasmaFuelUnlocked;
    private long productiveTicks;
    private long workUnits;
    private long generatorWorkTickRemainder;
    private BigInteger energyThroughput = BigInteger.ZERO;

    void recordConsumerTick(long consumedEU) {
        recordConsumerTick(consumedEU, true);
    }

    void recordConsumerTick(long consumedEU, boolean progressed) {
        if (progressed) productiveTicks = saturatingAdd(productiveTicks, 1L);
        hasPermanentPlasmaFuelDiscount();
        addEnergy(consumedEU);
    }

    void recordStartupEnergy(long consumedEU) {
        addEnergy(consumedEU);
    }

    void recordGeneratorTick(long acceptedEU, int runningMachines) {
        productiveTicks = saturatingAdd(productiveTicks, 1L);
        hasPermanentPlasmaFuelDiscount();
        addEnergy(acceptedEU);
        generatorWorkTickRemainder = saturatingAdd(generatorWorkTickRemainder, Math.max(0, runningMachines));
        if (generatorWorkTickRemainder >= 20L) {
            workUnits = saturatingAdd(workUnits, generatorWorkTickRemainder / 20L);
            generatorWorkTickRemainder %= 20L;
        }
    }

    void recordCompletedRecipes(long completed) {
        workUnits = saturatingAdd(workUnits, Math.max(0L, completed));
    }

    int getRuntimeLevel() {
        return level(productiveTicks, HostedMilestoneConfig.runtimeTicks);
    }

    int getWorkLevel() {
        return level(workUnits, HostedMilestoneConfig.workUnits);
    }

    int getEnergyLevel() {
        int level = 0;
        while (level < HostedMilestoneConfig.energy.length && energyThroughput.compareTo(HostedMilestoneConfig.energy[level]) >= 0) level++;
        return level;
    }

    int getCoordinationLevel() {
        return Math.min(getRuntimeLevel(), Math.min(getWorkLevel(), getEnergyLevel()));
    }

    int getSpeedTier() {
        return tierAtLevels(getRuntimeLevel());
    }

    int getParallelTier() {
        return tierAtLevels(getWorkLevel());
    }

    int getTaskTier() {
        return tierAtLevels(Math.min(getRuntimeLevel(), getWorkLevel()));
    }

    int getYieldTier() {
        return tierAtLevels(getCoordinationLevel());
    }

    int getPowerTier() {
        return tierAtLevels(getEnergyLevel());
    }

    boolean hasVoltageExtension() {
        return getEnergyLevel() >= HostedMilestoneConfig.voltageLevel;
    }

    int getMaximumTasks(int hostedMachines) {
        int count = Math.max(1, hostedMachines);
        int tier = getTaskTier();
        int limit = tier == 0 ? 1 : HostedMilestoneConfig.taskLimit[tier - 1];
        return limit == 0 ? count : Math.min(count, limit);
    }

    int applyParallelBonus(int baseParallel, int hostedMachines) {
        int tier = getParallelTier();
        int multiplier = tier == 0 ? 1 : HostedMilestoneConfig.parallelMultiplier[tier - 1];
        if (multiplier == 0) multiplier = Math.max(1, hostedMachines);
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(1, baseParallel) * multiplier);
    }

    int applyConsumerDuration(int ticks) {
        if (ticks <= 0) return ticks;
        return (int) Math.max(1L, ceilMultiplyDivide(ticks, durationPercent(), 100));
    }

    double getConsumerDurationMultiplier() {
        return durationPercent() / 100D;
    }

    long applyConsumerPower(long eut) {
        return eut <= 0 ? eut : Math.max(1L, ceilMultiplyDivide(eut, powerPercent(), 100));
    }

    long getNativePowerBudget(long availablePower, int efficiency) {
        if (availablePower == Long.MAX_VALUE) return Long.MAX_VALUE;
        if (availablePower <= 0) return 0L;
        return BigInteger.valueOf(availablePower).multiply(BigInteger.valueOf(100))
            .divide(BigInteger.valueOf(powerPercent()))
            .multiply(BigInteger.valueOf(Math.max(1000, Math.min(10000, efficiency))))
            .divide(BigInteger.valueOf(10000L))
            .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    int applyGeneratorDuration(int ticks, int hostedMachines) {
        if (ticks <= 0) return ticks;
        int parallel = applyParallelBonus(1, hostedMachines);
        BigInteger numerator = BigInteger.valueOf(ticks).multiply(BigInteger.valueOf(10000L))
            .multiply(BigInteger.valueOf(parallel)).multiply(BigInteger.valueOf(4L + getYieldQuarterBonus()))
            .multiply(BigInteger.valueOf(hasVoltageExtension() ? 4 : 1));
        BigInteger denominator = BigInteger.valueOf((long) durationPercent() * powerPercent() * 4);
        return numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator)
            .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    int getYieldQuarterBonus() {
        int tier = getYieldTier();
        return tier == 0 ? 0 : HostedMilestoneConfig.yieldQuarterBonus[tier - 1];
    }

    String getDurationMultiplier() {
        return String.format(java.util.Locale.ROOT, "%.2f", durationPercent() / 100D);
    }

    String getParallelMultiplier(int hostedMachines) {
        return String.valueOf(applyParallelBonus(1, hostedMachines));
    }

    String getYieldMultiplier() {
        return String.format(java.util.Locale.ROOT, "%.2f", 1D + getYieldQuarterBonus() / 4D);
    }

    String getPowerMultiplier() {
        return String.format(java.util.Locale.ROOT, "%.2f", powerPercent() / 100D);
    }

    long getProductiveTicks() {
        return productiveTicks;
    }

    long getWorkUnits() {
        return workUnits;
    }

    BigInteger getEnergyThroughput() {
        return energyThroughput;
    }

    long getNextRuntimeTarget() {
        int level = getRuntimeLevel();
        return level >= HostedMilestoneConfig.runtimeTicks.length ? -1L : HostedMilestoneConfig.runtimeTicks[level];
    }

    long getNextWorkTarget() {
        int level = getWorkLevel();
        return level >= HostedMilestoneConfig.workUnits.length ? -1L : HostedMilestoneConfig.workUnits[level];
    }

    BigInteger getNextEnergyTarget() {
        int level = getEnergyLevel();
        return level >= HostedMilestoneConfig.energy.length ? null : HostedMilestoneConfig.energy[level];
    }

    private int durationPercent() {
        int tier = getSpeedTier();
        return tier == 0 ? 100 : HostedMilestoneConfig.durationPercent[tier - 1];
    }

    private int powerPercent() {
        int tier = getPowerTier();
        return tier == 0 ? 100 : HostedMilestoneConfig.powerPercent[tier - 1];
    }

    boolean hasPermanentPlasmaFuelDiscount() {
        plasmaFuelUnlocked |= productiveTicks >= HostedMilestoneConfig.plasmaFuelTicks();
        return plasmaFuelUnlocked;
    }

    long getPlasmaForgeRuntime() {
        return HostedPlasmaForgeSupport.nativeRuntime(productiveTicks, HostedMilestoneConfig.plasmaFuelTicks(),
            hasPermanentPlasmaFuelDiscount());
    }

    void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean("plasmaFuelUnlocked", hasPermanentPlasmaFuelDiscount());
        tag.setLong("productiveTicks", productiveTicks);
        tag.setLong("workUnits", workUnits);
        tag.setLong("generatorWorkRemainder", generatorWorkTickRemainder);
        tag.setByteArray("energyThroughput", energyThroughput.toByteArray());
    }

    void readFromNBT(NBTTagCompound tag) {
        plasmaFuelUnlocked = tag.getBoolean("plasmaFuelUnlocked");
        productiveTicks = Math.max(0L, tag.getLong("productiveTicks"));
        workUnits = Math.max(0L, tag.getLong("workUnits"));
        generatorWorkTickRemainder = Math.max(0L, tag.getLong("generatorWorkRemainder")) % 20L;
        byte[] energy = tag.getByteArray("energyThroughput");
        energyThroughput = energy.length == 0 ? BigInteger.ZERO : new BigInteger(energy);
        if (energyThroughput.signum() < 0) energyThroughput = BigInteger.ZERO;
    }

    private void addEnergy(long amount) {
        if (amount > 0) energyThroughput = energyThroughput.add(BigInteger.valueOf(amount));
    }

    private static int tierAtLevels(int level) {
        int tier = 0;
        while (tier < 3 && level >= HostedMilestoneConfig.bonusLevels[tier]) tier++;
        return tier;
    }

    private static int level(long value, long[] thresholds) {
        int level = 0;
        while (level < thresholds.length && value >= thresholds[level]) level++;
        return level;
    }

    private static long ceilMultiplyDivide(long value, long numerator, long denominator) {
        if (value <= 0 || numerator <= 0 || denominator <= 0) return 0;
        return BigInteger.valueOf(value).multiply(BigInteger.valueOf(numerator))
            .add(BigInteger.valueOf(denominator - 1)).divide(BigInteger.valueOf(denominator))
            .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
