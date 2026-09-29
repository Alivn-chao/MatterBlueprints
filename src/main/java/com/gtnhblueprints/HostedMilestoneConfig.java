package com.gtnhblueprints;

import java.math.BigInteger;

import net.minecraftforge.common.config.Configuration;

/** Server-side milestone settings, read on startup. Invalid lists fall back as a whole. */
public final class HostedMilestoneConfig {

    public static long[] runtimeTicks = { 12000, 72000, 432000, 1728000, 5184000, 12096000 };
    public static long[] workUnits = { 1000, 10000, 100000, 500000, 2000000, 10000000 };
    public static BigInteger[] energy = big(new String[] { "100000000000", "10000000000000",
        "1000000000000000", "100000000000000000", "10000000000000000000", "1000000000000000000000" });
    public static int[] durationPercent = { 75, 50, 25 };
    public static int[] powerPercent = { 75, 50, 25 };
    public static int[] parallelMultiplier = { 2, 4, 0 };
    public static int[] taskLimit = { 4, 16, 0 };
    public static int crossRecipeTasks = 64;
    public static int[] yieldQuarterBonus = { 1, 2, 4 };
    public static int[] bonusLevels = { 1, 3, 6 };
    public static int voltageLevel = 5;
    public static int plasmaFuelHours = 8;
    public static int advancedAssemblyFixedParallel = 0;
    public static int advancedAssemblyNormalLevel = 3;
    public static int advancedAssemblyPerfectLevel = 6;

    private HostedMilestoneConfig() {}

    public static void load(Configuration config) {
        String category = "hostMilestones";
        crossRecipeTasks = config.getInt("crossRecipeTasks", category, 64, 1, 1024,
            "Maximum simultaneous recipe tasks sharing host parallel and power. / 跨配方任务上限，共享总并行与供电。");
        advancedAssemblyFixedParallel = config.getInt("advancedAssemblyFixedParallel", category, 0, 0, 16,
            "Advanced assembly base parallel: 0 = recipe item input count; 16 = fixed sixteen. / 进阶装配线基础并行，0=物品输入项数。");
        advancedAssemblyNormalLevel = config.getInt("advancedAssemblyNormalLevel", category, 3, 1, 6,
            "Energy level replacing laser OC with regular OC. / 普通超频解锁能量等级。");
        advancedAssemblyPerfectLevel = config.getInt("advancedAssemblyPerfectLevel", category, 6,
            advancedAssemblyNormalLevel, 6, "Energy level unlocking perfect OC. / 无损超频解锁能量等级。");
        runtimeTicks = longs(config, category, "runtimeTicks", runtimeTicks,
            "Six strictly increasing productive-time thresholds in ticks (20 ticks = 1 second). / 六级有效工作时间，单位 tick。");
        workUnits = longs(config, category, "workUnits", workUnits,
            "Six strictly increasing completed-work thresholds. / 六级完成工作量。");
        String[] defaults = new String[energy.length];
        for (int i = 0; i < energy.length; i++) defaults[i] = energy[i].toString();
        String[] configured = config.get(category, "energyEU", defaults,
            "Six increasing EU thresholds, decimal integers; supports values beyond long. / 六级能量吞吐，十进制整数。").getStringList();
        try {
            BigInteger[] parsed = big(configured);
            if (parsed.length != 6) throw new IllegalArgumentException();
            BigInteger previous = BigInteger.ZERO;
            for (BigInteger value : parsed) {
                if (value.compareTo(previous) <= 0) throw new IllegalArgumentException();
                previous = value;
            }
            energy = parsed;
        } catch (RuntimeException error) {
            config.get(category, "energyEU", defaults).set(defaults);
        }
        durationPercent = ints(config, "durationPercent", durationPercent, 1, 100,
            "Three duration percentages. / 三档耗时百分比，越小越快。");
        powerPercent = ints(config, "powerPercent", powerPercent, 1, 100,
            "Three power percentages. / 三档耗电百分比。");
        parallelMultiplier = ints(config, "parallelMultiplier", parallelMultiplier, 0, 1000000,
            "Three parallel multipliers; 0 means hosted machine count. / 三档并行倍率，0=托管机器数。");
        taskLimit = ints(config, "taskLimit", taskLimit, 0, 1000000,
            "Three task limits; 0 means hosted machine count. / 三档任务上限，0=托管机器数。");
        yieldQuarterBonus = ints(config, "yieldQuarterBonus", yieldQuarterBonus, 0, 400,
            "Three output bonuses in quarters: 1=+25%, 2=+50%, 4=+100%. / 三档额外产量，每点25%。");
        int[] levels = ints(config, "bonusLevels", bonusLevels, 1, 6,
            "Three strictly increasing milestone levels unlocking bonus tiers. / 三档加成解锁等级，递增。");
        if (levels[0] < levels[1] && levels[1] < levels[2]) bonusLevels = levels;
        else config.get(category, "bonusLevels", bonusLevels).set(bonusLevels);
        voltageLevel = config.getInt("voltageLevel", category, 5, 1, 6, "Voltage extension energy level. / 电压扩展所需能量等级。");
        // The old level-based gate must not keep existing installations at the week-long default.
        config.getCategory(category).remove("plasmaFuelLevel");
        plasmaFuelHours = config.getInt("plasmaFuelHours", category, 8, 1, 87600,
            "Productive host hours permanently unlocking 50% plasma forge catalyst reduction. Independent of milestone levels. / 煅炉永久50%催化剂减免所需有效运行小时数，独立于里程碑等级。");
    }

    public static long plasmaFuelTicks() {
        return Math.max(1L, plasmaFuelHours) * 72000L;
    }

    private static int[] ints(Configuration config, String key, int[] defaults, int min, int max, String comment) {
        int[] values = config.get("hostMilestones", key, defaults, comment).getIntList();
        boolean valid = values.length == 3;
        for (int value : values) valid &= value >= min && value <= max;
        if (valid) return values;
        config.get("hostMilestones", key, defaults).set(defaults);
        return defaults;
    }

    private static long[] longs(Configuration config, String category, String key, long[] defaults, String comment) {
        String[] strings = new String[defaults.length];
        for (int i = 0; i < defaults.length; i++) strings[i] = Long.toString(defaults[i]);
        try {
            return parseThresholds(config.get(category, key, strings, comment).getStringList());
        } catch (RuntimeException error) {
            config.get(category, key, strings).set(strings);
            return defaults;
        }
    }

    public static long[] parseThresholds(String[] values) {
        if (values.length != 6) throw new IllegalArgumentException("Exactly six thresholds required");
        long[] result = new long[6];
        long previous = 0;
        for (int i = 0; i < result.length; i++) {
            result[i] = Long.parseLong(values[i].trim());
            if (result[i] <= previous) throw new IllegalArgumentException("Thresholds must increase and be positive");
            previous = result[i];
        }
        return result;
    }

    private static BigInteger[] big(String[] values) {
        BigInteger[] result = new BigInteger[values.length];
        for (int i = 0; i < values.length; i++) result[i] = new BigInteger(values[i].trim());
        return result;
    }
}
