# 0.4.18 托管超频检查记录

检查对象：项目当前使用的 GT5-Unofficial 5.09.54.133。此次是检查与回归验证，未修改运行时代码，未发布新版本。

## 纳米煅炉的高安培

没有复现进阶装配线先前的同类错误。

原生 `MTENanoForge.setProcessingLogicPower()` 使用 `getMaxInputEu()` 设置计算器的可用电压，再设置 1A、禁用安培超频并允许跨级。这里的 `getMaxInputEu()` 已经通过 `ExoticEnergyInputHelper.getTotalEuMulti()` 汇总能源仓的电压与安培，因此“1A”不表示只按一个物理安培计算。

当前托管入口保留原生设置，并对其最终功率做预算截断；没有像旧进阶装配线一样，传入物理电压和高安培后又把安培截掉。

针对当前依赖增加了实际计算器测试：125,829,120 EU/t 配方，比较 UMV 1A 与 UMV 65536A 对应的折算功率。前者无额外超频，后者得到 8 次普通超频，耗电未超过给定功率。这个测试证明功率折算模型能利用安培，并非完整游戏世界的纳米煅炉配方重放。

原生普通/无损超频还受煅炉结构等级、配方特殊等级及四阶特殊并行条件影响，不能仅按输入安培判断无损超频是否应该生效。

## 纳米煅炉另外存在的托管冲突（尚未修复）

1. 原生 `validateRecipe()` 会重新写入 `maxParallel`：未触发四阶磁物质机制时通常回到 1。这个写入发生在托管的并行供应器执行后，会覆盖中心聚合与里程碑分配的并行。
2. 原生 `createOverclockCalculator()` 会重新设置 `durationModifier`，值为原生四阶速度系数或 1，覆盖 `HostedProcessingSettings` 提前提供的中心耗时倍率。中心仍会把该流程视为已经应用速度加成，因此不会在任务捕获后补乘。

后续修复需要保留原生结构等级、磁物质/纳米材料消耗及原生特殊并行，不能通过一律开启安培超频或事后压缩时间解决；事后压缩还会漏掉不足 1 tick 的产量收益。

## 其他机器

对依赖 jar 的 4,593 个根目录类文件做了静态扫描，识别出 338 个多方块类，列出了 73 个供电/超频相关方法。结果只证明这些入口已经检查，不等同于逐台游戏内实测；不包含其他未接入依赖的第三方机器。

- 通用 `MTEMultiBlockBase` 供电路径启用了安培参与超频；此次未再确认类似旧进阶装配线的高安培截断。
- 发热炉、吸热冰箱、巨型合金冶炼炉、巨型蒸馏塔、巨型裂化机、组件装配线等使用总功率折算到电压、配 1A 的原生模型，不能仅因为出现 `setAvailableAmperage(1)` 就修改。
- 普通装配线禁用安培超频、聚变反应堆采用专门超频规则、太空组装机采用模块等级电压规则，各有原生设计依据，不能统一解除限制。
- 另外确认精密组装机、组件装配线、PCB 工厂会在各自的 `createOverclockCalculator()` 中重写耗时系数，存在与纳米煅炉相同的“中心耗时加成被覆盖”路径。它们与高安培丢失是不同问题，本轮尚未修复。
- 巨型合金冶炼炉虽然也重写耗时系数，但项目已有 `HostedThermalMachineSupport` 临时调整原生 `speedBonus` 字段的专门适配，不能直接归入上述未修复列表。

原始扫描结果在工作区构建目录 `build/overclock-audit/native-power-calls.txt`，对应扫描程序 `build/overclock-audit/Audit.java`。新增 `HostedNativeOverclockAuditTest` 覆盖原生功率入口、实际高安培折算计算及关键覆写契约。

## 原生来源

- [纳米煅炉](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.133/src/main/java/gregtech/common/tileentities/machines/multi/MTENanoForge.java#L425)
- [精密组装机](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.133/src/main/java/goodgenerator/blocks/tileEntity/MTEPreciseAssembler.java#L210)
- [组件装配线](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.133/src/main/java/goodgenerator/blocks/tileEntity/MTEComponentAssemblyLine.java#L302)
- [PCB 工厂](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.133/src/main/java/gregtech/common/tileentities/machines/multi/pcb/MTEPCBFactory.java#L432)
