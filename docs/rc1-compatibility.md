# GTNH 2.9.0-RC-1 兼容说明

本适配以当前 0.3.28-beta3 功能为基础，产物版本为 **0.3.28-rc1**。

依赖版本依据 [GTNH RC-1 发布页](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/releases/tag/2.9.0-RC-1)
和 [官方构建清单](https://github.com/GTNewHorizons/DreamAssemblerXXL/blob/master/releases/manifests/2.9.0-RC-1.json) 对齐：

| 依赖 | beta-3 构建 | RC-1 构建 |
| --- | --- | --- |
| GT5-Unofficial | 5.09.54.132 | 5.09.54.183 |
| GTNewHorizonsCoreMod | 2.9.61 | 2.9.76 |
| Matter Manipulator | 0.1.55-GTNH | 0.1.59-GTNH |
| GTNHLib | 0.11.46 | 0.11.51 |
| StructureLib | 1.4.42 | 1.4.45 |
| Applied Energistics 2 | rv3-beta-1050-GTNH | rv3-beta-1073-GTNH |
| ModularUI2 | 2.3.88-1.7.10 | 2.3.91-1.7.10 |

ModularUI 1.3.4 与 Waila 1.19.34 保持不变。Core 版本用于蓝图元数据和兼容提示，不是直接编译依赖。

## 安装及已有蓝图

客户端和服务器同时将旧版 Matter Blueprints JAR 替换为 `matterblueprints-0.3.28-rc1.jar`，不要同时保留两个版本。
RC-1 版要求运行环境中的 Matter Manipulator 为 0.1.59-GTNH。

蓝图格式仍为 schema 2。旧 beta-3 蓝图的来源版本会产生警告，不会仅因为来源版本较旧而被拒绝；
缺失模组、无法解析方块、无效格式或超出配置限制仍会阻止使用。原有配置键不变。
后续批处理修正增加了向后兼容的任务容量字段，见 [批处理与超频说明](batch-subtick-hosting.md)。

## 验证范围

- 在上述 RC-1 依赖下完成 `gradlew.bat build --offline`，编译、Checkstyle、重混淆打包通过。
- 当前修正版 105 项自动测试通过，失败、错误和跳过均为 0（初次 RC-1 适配为 96 项）。
- 新增 3 项反射接口回归测试，检查 MM 搭建队列、原生规划处理器和整数规划标志。
- 对照 MM 0.1.55 → 0.1.59 与 GT 5.09.54.132 → 5.09.54.183 源码差异检查了涉及的接口。

尚未在完整 RC-1 客户端及专用服务器中实测。进服后应复核旧蓝图导入、旋转/堆叠、原生规划下单、
实际搭建，以及托管机器运行和存档重载；自动测试不能替代这些游戏内验证。
