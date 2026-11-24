# mock-location-oss

一个开源的 Android 模拟定位应用：把手机的系统定位替换成你在地图上选定的坐标或一条按速度移动的路线。

本项目是对应用市场应用「位置模拟器」（包名 `com.yangtools.gps.simulator`，版本 2.9.12）**公开可见功能的独立重写**。它不含原软件的任何代码、资源或密钥，也不做破解；它用 Android 官方的「模拟位置应用（mock location app）」机制实现同样的能力，并把原软件依赖的私有服务（百度/谷歌地图 SDK、Firebase、Google Play 订阅、PairIP 授权）替换成可自由分发、无需密钥的等价物。

- 复刻目标与差异的完整说明：`docs/analysis/spec/02-开源复刻方案.md`
- 原软件模拟定位机制的逆向结论（含证据行号）：`docs/analysis/spec/01-模拟定位实现机制.md`
- 逐项复刻对照表（行为 → 实现 → 验证方式）：`docs/复刻对照表.md`
- 原软件逐屏行为规格：`docs/analysis/`（12 篇逐屏行为规格）

## 功能

| 功能 | 说明 |
|---|---|
| 单点模拟 | 地图点选或输入经纬度，设定后每秒把该坐标写入系统定位 |
| 路线模拟 | 在地图上连续点选路线点，设定 0–300 km/h 的速度，按真实经过时间沿折线推进；支持循环、暂停/继续、进度百分比、走完后保持终点 |
| 双定位通道 | `LocationManager` 的 `gps` 与 `network` 两个测试 provider 同时注入；设备有 Google Play 服务时额外写入 Fused 定位 mock（反射调用，无编译期依赖） |
| 后台运行 | 前台服务 + 常驻通知（暂停路线 / 停止模拟按钮）+ `PARTIAL_WAKE_LOCK`，熄屏后继续注入；进程被杀由 `START_REDELIVER_INTENT` 自动恢复 |
| 收藏 / 历史 / 我的路线 | SQLite 持久化，表结构与字段名与原软件一致（`records`、`saved_routes`） |
| 坐标系 | WGS84 / GCJ-02 / BD-09 三种坐标系的输入与显示，内部统一以 WGS84 注入 |
| 地址搜索 | OpenStreetMap Nominatim 搜索 + 本地结果缓存（最多 5 条）+ 坐标直输 |
| 自检与引导 | 开发者选项、模拟位置应用、系统定位、定位权限、通知权限、电池优化六项检查，未通过给跳转入口 |
| 评价提示 | 第 3 次成功启动后弹本地点赞提示（不跳应用商店） |

## 运行前提

1. Android 7.0（API 24）及以上。
2. 在系统「开发者选项」里把本应用设为**模拟位置应用**；这是注入测试 provider 的唯一系统授权，没有它任何实现都无法模拟定位。
3. 授予定位权限；Android 13 及以上再授予通知权限。

## 构建

本工程在本机（Windows + MSYS bash）用一套离线工具链验证过；脚本已把 JDK、Gradle、Android SDK、临时目录、调试签名全部固定，换机器只需覆盖三个环境变量。

```bash
cd mock-location-oss
./build-debug.sh                     # 产物：app/build/outputs/apk/debug/app-debug.apk
```

工具链默认值（可用同名 `*_OVERRIDE` 环境变量替换）：

| 组件 | 默认路径 |
|---|---|
| JDK 21 | `E:/software/ss/Deepseek-Harness/build-tools/jdk-extract/jdk-21.0.12.1+1` |
| Gradle 8.2 | `E:/software/ss/Deepseek-Harness/build-tools/gradle-8.2` |
| Android SDK 34 | `E:/software/ss/Deepseek-Harness/build-tools/android-sdk` |
| Gradle 依赖缓存 | `E:/software/ss/Deepseek-Harness/build-tools/gradle-home` |

构建细节与遇到的环境坑见 `BUILD.md`；集成与验证证据见 `VERIFY.md`。

## 自测

核心几何、路线算法与 1 秒 tick 状态机都是纯 Java 类，可在没有 Android 设备的情况下用 JVM 直接验证（共 64 项断言）：

```bash
./tools/run-selftest.sh
```

## 结构

```
app/src/main/java/io/github/mocklocation/oss/
  MainActivity.java                  单 Activity + 抽屉导航，页面切换与权限
  core/MockEngine.java               LocationManager 双 provider + GMS Fused 反射 mock + AppOps 检测
  core/MockState.java                状态契约（running/mode/paused/routeCompleted/位置/速度/进度/错误）
  core/SimulationEngine.java         1 秒 tick 状态机（单点/路线/暂停/循环/完成），纯 Java 可 JVM 自测
  core/LocationFields.java           一次注入的 Location 字段（精度/高度/速度/方位/时间），纯 Java 可 JVM 自测
  core/RouteEngine.java              折线总长、按距离取点、循环取模、终点保持、进度
  core/GeoMath.java                  haversine 距离（R=6371000）、方位角、线性插值
  core/CoordinateConverter.java      WGS84 / GCJ-02 / BD-09 互转
  service/MockLocationService.java   前台服务、1 秒 tick、路线推进、通知、WakeLock
  data/                              SQLite（records / saved_routes）、设置、搜索缓存
  ui/                                地图页控制器、坐标输入、收藏/历史/路线/设置页、自检对话框
app/src/main/assets/                 Leaflet 地图页与本地 JS/CSS（无需任何地图密钥）
```

## 与原软件的差异

| 方面 | 原软件 | 本项目 | 原因 |
|---|---|---|---|
| 地图 | 百度地图（中国）/ Google 地图（海外） | Leaflet + OpenStreetMap（可切 CARTO / OpenTopoMap） | 百度、Google 需要私有 API key 且 Google 需要 GMS |
| 地址搜索 | 百度 Place API / Google Places | OpenStreetMap Nominatim | 同上 |
| 订阅付费 | Play Billing 月/年订阅，未订阅不能模拟 | 完全免费 | 商业能力不属于模拟定位功能 |
| 统计与远程配置 | Firebase Firestore / Remote Config / 安装统计 | 无 | 需要 Google Cloud 项目 |
| 授权校验 | PairIP | 无 | 属于分发保护 |
| Fused mock 实现 | 自行拼 GMS binder（事务 13/85） | 反射调用公开 API `setMockMode`/`setMockLocation` | 不引入 GMS 编译依赖，功能等价 |

模拟定位机制本身（双 provider 注入、GMS mock、1 秒 tick、路线算法、通知、自检、持久化）与原软件保持一致。

## 许可

MIT，见 `LICENSE`。

本项目是对「位置模拟器」（`com.yangtools.gps.simulator` 2.9.12）公开可见功能的**独立重写**：源码全部由本项目作者编写，不包含原软件的反编译代码、资源、密钥或商标；原软件名称与包名仅用于说明兼容目标。本项目仅用于学习与个人测试；用模拟定位欺骗需要真实位置的业务可能违反其服务条款与当地法律，责任由使用者承担。
