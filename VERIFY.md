# VERIFY：构建与验证证据

本文记录 `mock-location-oss` 在 2026-11-24 这一版的构建结果、可以在没有 Android 设备的机器上完成的验证，以及仍需要在真机上走查的项目。所有数字都来自本机实测，不是估算。

## 1. 构建可复现性

命令（工程根目录）：

```bash
./build-debug.sh
```

最近一次**先删除 `app/build` 再全量构建**的结果：

| 项目 | 值 |
|---|---|
| 结果 | `BUILD SUCCESSFUL in 18s`（删掉 `app/build` 后全量构建，31 个 task 全部执行；增量构建约 3–10 秒） |
| 产物 | `app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | 5,367,883 字节 |
| SHA-256 | `af22cb02aec6f044f645c54b4489b762b9522900d855a4c76b2d36bc79b26d16` |
| 工具链 | JDK 21.0.12.1（Temurin）· Gradle 8.2 · AGP 8.2.0 · Android SDK platform 34 / build-tools 34.0.0 |
| 依赖 | 全部来自 `build-tools/gradle-home` 缓存，`--offline` 构建，不联网 |

构建脚本固定了四件事，换机器时只需覆盖同名 `*_OVERRIDE` 变量：JDK、Gradle 发行版、SDK、Gradle 缓存；同时把 `java.io.tmpdir` 指到工程内 `.tmp`，避开本机 `C:\WINDOWS\TEMP` 上历史临时目录不可写导致的 `MergeJavaRes` 失败（该坑已记录在 `BUILD.md` 与脚本注释里）。

## 2. 核心引擎 JVM 自测：64/64 通过

```bash
./tools/run-selftest.sh
```

分两组：几何与坐标系 28 项、模拟状态机与 Location 字段 36 项，实测输出最后两行分别是
`通过 28 项，失败 0 项` 与 `通过 36 项，失败 0 项`。

第一组（几何与坐标系）覆盖的内容与关键数值：

| 类别 | 断言 | 实测 |
|---|---|---|
| 距离 | 北京—上海约 1063 km（R=6371000，与原实现常量一致） | 通过（误差 < 30 km） |
| 距离 | 同一点距离为 0 | 0.0 |
| 方位 | 正北 0°、正东 90° | 0.0° / 90.0° |
| 插值 | 短线段中点两侧距离相等 | 差 < 0.05 m |
| 插值 | 长线段中点是经纬度线性中点（原实现的线性插值） | 差 < 0.001 m |
| 坐标系 | 北京 WGS84→GCJ-02 偏移 | 554.9 m（合理区间 100–800 m） |
| 坐标系 | GCJ-02 往返误差 | < 0.5 m |
| 坐标系 | 北京 WGS84→BD-09 偏移 | 1374.8 m（合理区间 200–1500 m） |
| 坐标系 | BD-09 往返误差 | < 0.5 m |
| 坐标系 | 境外不做 GCJ-02 偏移（东京） | 0.0 m |
| 路线 | 两公里线折线总长 | 1000.00 m（构造 1000 m 线段） |
| 路线 | 起点/中点/终点坐标与进度 | 0 / 0.5 / 1.0，误差 < 1 m |
| 路线 | 中点航向 | 0°（正北） |
| 路线 | 非循环越过终点 | 停在终点，`finished=true`，进度 1.0 |
| 路线 | 循环模式 1250 m 于 1000 m 环线 | 回到 250 m 处，永不 finished |
| 路线 | 过弯后航向 | 89.998°（正东） |

第二组（`SimulationEngine` + `LocationFields` + `MockState`）验证的是原软件最核心的 1 秒 tick 语义，
全部通过：单点模式每秒原样注入且速度/方位为 0；路线模式按真实时间差 dt 推进（首秒 10 米、被延迟后一次补齐到 30 米）；
暂停时仍返回采样但注入速度为 0 且不累计位移，继续后无跳变；非循环路线走完 `routeCompleted=true` 且 `running` 仍为 true、
继续注入终点、注入速度 0；循环路线对总长取模、永不完成；GPS 精度 3 米、network 精度 10 米、高度 0、
速度精度 0.2 m/s、方位精度 2°、垂直精度等于水平精度；状态模型进度百分比 clamp 与注入速度语义正确。

## 3. APK 静态验证

用 SDK 自带 `aapt2 dump badging` 检查产物：

```
package: name='io.github.mocklocation.oss' versionCode='1' versionName='0.1.0'
sdkVersion:'24'  targetSdkVersion:'34'  compileSdkVersion='34'
uses-permission: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_NETWORK_STATE,
                 INTERNET, WAKE_LOCK, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION,
                 POST_NOTIFICATIONS
application-label:'位置模拟器（开源复刻）'
launchable-activity: io.github.mocklocation.oss.MainActivity
```

APK 内容抽检：

- `assets/map.html`（4,281 字节）、`assets/leaflet.js`（147,557 字节）、`assets/leaflet.css`（14,806 字节）与 4 个图标资源都在包内；
- dex 中确认包含 `MainActivity`、`MockLocationService`、`MapPageController`、`SelfCheckDialog`、`NominatimClient`、`PlaceListFragment`、`UiHost` 等本工程类。

## 4. 代码级一致性审计

| 审计 | 方法 | 结果 |
|---|---|---|
| JS 桥函数名 | 对比 `MapPageController` 调用的 JS 函数与 `map.html` 定义的函数 | 10 个函数两边完全一致（setTarget/clearTarget/setRoute/clearRoute/setMode/centerOn/fitRoute/setHere/clearHere/setTileLayer） |
| 格式化字符串 | 解析 185 条 strings 的占位符个数，与 40 处 `getString(R.string.x, ...)` 实参个数逐个比对 | 0 处不匹配（避免运行时 `MissingFormatArgumentException`） |
| 资源引用 | ui-dev 交付前用 `aapt2 compile --dir res` 编译全部资源，并交叉检查 layout/menu 里的 `@string/@drawable/@layout` 引用 | 退出码 0，无未解析引用 |
| 编译 | 本工程 Gradle 全量构建 | 无错误，无警告阻断 |
| 数据库结构 | `records(id,name,latitude,longitude,type,timestamp)` 与 `saved_routes(id,name,speedKmh,loop,points,updatedAt)` 与原软件 Room 建表一致 | 字段名与类型一致，`points` 采用 `lat,lng;lat,lng` 文本编码 |

## 5. 设备行为自动化验证（脚本已就绪）

`tools/device-verify.sh` 会在一台已连接 adb 的设备/模拟器上执行端到端行为验证，而不是只看编译结果：

1. 安装 APK（`-g` 自动授予运行时权限）；
2. 用 `appops set <pkg> android:mock_location allow` 把应用设为模拟位置应用（等价于开发者选项里手动选择）；
3. 打开系统定位；
4. 通过 **debug 专用指令入口**（`am start -a io.github.mocklocation.oss.VERIFY --es command start_static --es point "39.9042,116.4074"`）启动单点模拟，然后从 `dumpsys location` 读取系统 `gps` provider 的坐标并比对；
5. 启动三点路线（36 km/h），隔 3 秒采样两次，断言坐标在移动；
6. 触发暂停，再隔 3 秒采样两次，断言坐标不再变化；
7. 停止模拟，断言 `dumpsys location` 中已无 mock location 记录；
8. 截图保存到 `tools/verify-out/`。

该入口只在 `BuildConfig.DEBUG` 构件里生效（`MainActivity.handleVerifyIntent`），release 包不会响应任何 `VERIFY` 指令，因此不会成为产品面的后门。

```bash
./tools/device-verify.sh            # 默认用 app/build/outputs/apk/debug/app-debug.apk
```

本机已安装好 Android 模拟器（`emulator` + `system-images;android-34;google_apis;x86_64`，AVD 名 `verify34`），
但设备级执行目前受宿主环境阻塞，原因记录如下（不是应用缺陷）：

- WHPX 加速路径：`emulator -accel on` 能启动，QEMU 报告 "Windows Hypervisor Platform accelerator is operational"，
  但 guest 一条指令都不执行（QEMU 进程 CPU 时间停在 0.45 秒不再增长，`adb devices` 永远 `offline`）。
  这台机器的 Hyper-V 可用性报告为 True，但宿主本身运行在虚拟化环境里，嵌套虚拟化下 WHPX 不可用。
- 软件模拟（TCG，`-accel off` + `ANDROID_I_WANT_MY_TCG=yes`）同样不可用：emulator 自身警告
  "x86_64 emulation may not work without hardware acceleration"，启动后 QEMU 进程 CPU 时间同样停在 0.5 秒量级不再增长，
  `adb devices` 里始终没有设备。两条路径都指向同一结论：**这台机器所在的虚拟化环境不支持运行 Android 模拟器**。
  相关日志留在 `E:\software\ss\Deepseek-Harness\位置模拟器逆向\emu\emulator.log` 与 `emulator-tcg.log`。
- 因此 `tools/device-verify.sh` 目前只完成了脚本自检（参数、adb 调用、断言逻辑），尚未产出设备端通过记录。
  有真机时执行 `./tools/device-verify.sh` 即可得到第 7 节的完整结果。

## 6. 尚未验证（需要真机/模拟器）


本机只有 Android SDK（platform-tools、build-tools），**没有连接设备也没有安装模拟器**，因此下列「活体行为」只能留到有设备时执行；这不是已知缺陷，而是本轮验证边界：

1. 把应用设为「模拟位置应用」后，另一个应用或 `adb shell dumpsys location` 能否读到注入坐标；
2. 1 秒 tick 在熄屏/后台是否持续（WakeLock + 前台服务）；
3. 路线模拟的点位推进、暂停/恢复、循环、走完保持终点；
4. 通知栏三个动作按钮（暂停路线 / 继续路线 / 停止模拟）；
5. 进程被杀后 `START_REDELIVER_INTENT` 是否恢复模拟；
6. 收藏/历史/我的路线的增删改查与重启后持久化；
7. 六项自检在未授权/未开开发者选项时的提示与跳转；
8. WebView 地图在无网/有网下的表现（瓦片加载失败时是否有可用提示）。

设备就绪后按 `docs/analysis/spec/02-开源复刻方案.md` 第 5.2 节逐条走查，并把结果补进本文第 6 节。

## 7. 设备走查记录

（待补充：设备型号、Android 版本、每条用例的结果与截图路径）
