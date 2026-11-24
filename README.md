# MockLocation OSS

一个开源的 Android 模拟定位应用：把手机的系统定位替换成你在地图上选定的坐标，或一条按设定速度移动的路线。

应用通过 Android 官方的「模拟位置应用（mock location app）」机制工作，不需要 root、不需要 Xposed：
用户在系统开发者选项里把本应用选为模拟位置应用之后，本应用即可向前台服务持续写入模拟坐标。

## 功能

| 功能 | 说明 |
|---|---|
| 单点模拟 | 地图点选或输入经纬度，设定后每秒把该坐标写入系统定位 |
| 路线模拟 | 在地图上连续点选路线点，设定 0–300 km/h 的速度，按真实经过时间沿折线推进；支持循环、暂停/继续、进度百分比、走完后保持终点 |
| 双定位通道 | `LocationManager` 的 `gps` 与 `network` 两个测试 provider 同时注入；设备具备 Google Play 服务时额外写入 Fused 定位 mock（反射调用，无编译期依赖） |
| 后台运行 | 前台服务 + 常驻通知（暂停路线 / 停止模拟）+ `PARTIAL_WAKE_LOCK`；进程被杀后由 `START_REDELIVER_INTENT` 自动恢复 |
| 收藏 / 历史 / 我的路线 | SQLite 持久化；收藏与历史分表存储，路线以文本编码保存点列表 |
| 坐标系 | WGS84 / GCJ-02 / BD-09 三种坐标系的输入与显示；内部统一以 WGS84 注入 |
| 地址搜索 | OpenStreetMap Nominatim 搜索 + 本地结果缓存（最多 5 条）+ 坐标直输 |
| 自检与引导 | 开发者选项、模拟位置应用、系统定位、定位权限、通知权限、电池优化六项检查，未通过给跳转入口 |
| 设置 | 地图提供商（OSM / CARTO / OpenTopoMap）、坐标系、自检入口、关于 |

地图与搜索都不依赖任何私有 API key：地图是用打包在应用内的 Leaflet 加载在线瓦片，搜索走 OpenStreetMap 的公开服务。

## 运行前提

1. Android 7.0（API 24）及以上。
2. 在系统「开发者选项 → 选择模拟位置应用」里选中本应用；这是注入测试 provider 的唯一系统授权。
3. 授予定位权限；Android 13 及以上再授予通知权限。

## 下载安装

预发布版 APK（debug 签名，仅用于测试）：
<https://github.com/maple110011/location-simulator-oss/releases/download/v0.1.0/app-debug.apk>

## 构建

```bash
./build-debug.sh                     # 产物：app/build/outputs/apk/debug/app-debug.apk
```

该脚本固定了本机工具链（JDK / Gradle / Android SDK / 调试签名）并强制离线构建；
换机器或 CI 请直接使用 `gradle :app:assembleDebug`，或参考 `BUILD.md` 第 8 节的路径覆盖变量。

## 测试

核心几何、路线推进、1 秒 tick 状态机与注入参数都是纯 Java 类，不需要设备即可验证：

```bash
./tools/run-selftest.sh              # 90 项断言：几何 28 + 状态机 36 + 注入 26
node tools/fetch-test-deps.mjs       # 首次执行：下载 Robolectric 所需的 android-all jar
./tools/run-android-tests.sh         # 18 项 Robolectric 测试：AppOps 判定 / 服务 tick / SQLite / 全链路
```

设备（真机或模拟器）上的端到端验证：

```bash
./tools/device-verify.sh             # 安装、授权、启动单点/路线，用 dumpsys location 断言坐标
```

## 结构

```
app/src/main/java/io/github/mocklocation/oss/
  MainActivity.java                  单 Activity + 抽屉导航，页面切换与权限
  core/MockEngine.java               LocationManager 双 provider + GMS Fused mock + AppOps 判定
  core/MockInjector.java             注入编排：provider 建立/清理、每秒写入
  core/SimulationEngine.java         1 秒 tick 状态机（单点/路线/暂停/循环/完成）
  core/LocationFields.java           一次注入的 Location 字段
  core/RouteEngine.java              折线总长、按距离取点、循环取模、终点保持、进度
  core/GeoMath.java                  haversine 距离、方位角、线性插值
  core/CoordinateConverter.java      WGS84 / GCJ-02 / BD-09 互转
  service/MockLocationService.java   前台服务、1 秒 tick、路线推进、通知、WakeLock
  data/                              SQLite、设置、搜索缓存
  ui/                                地图页控制器、坐标输入、收藏/历史/路线/设置、自检对话框
app/src/main/assets/                 Leaflet 地图页与本地 JS/CSS
```

## 文档

- `docs/FEATURES.md`：功能与行为约定（模式、状态机、通知、数据模型、自检）。
- `docs/ARCHITECTURE.md`：模块划分与设计取舍。
- `BUILD.md`：构建环境、可复现命令、跨机器/CI 构建。
- `VERIFY.md`：构建与测试证据、验证边界。

## 许可

MIT，见 `LICENSE`。本项目仅用于学习与个人测试；用模拟定位欺骗需要真实位置的业务可能违反其服务条款与当地法律，责任由使用者承担。
