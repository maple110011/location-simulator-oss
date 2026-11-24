# VERIFY：构建与验证证据

本文记录 MockLocation OSS 这一版的构建结果、在没有 Android 设备的机器上能完成的验证，以及仍需要在真机上走查的项目。
所有数字都来自本机实测，不是估算。

## 1. 构建可复现性

命令（工程根目录）：

```bash
./build-debug.sh
```

最近一次**先删除 `app/build` 再全量构建**的结果：

| 项目 | 值 |
|---|---|
| 结果 | `BUILD SUCCESSFUL`（Gradle 8.2 增量构建约 1–10 秒；清空 `app/build` 后全量约 18 秒） |
| 产物 | `app/build/outputs/apk/debug/app-debug.apk` |
| 工具链 | JDK 21.0.12.1（Temurin）· Gradle 8.2 · AGP 8.2.0 · Android SDK platform 34 / build-tools 34.0.0 |
| 依赖 | 全部来自 `build-tools/gradle-home` 缓存，`--offline` 构建，不联网 |
| 签名 | 本机固定调试 keystore（可用 `-PmockLocationKeystore` 覆盖；不存在时回退到 AGP 自动生成的 debug keystore） |

构建脚本固定了 JDK、Gradle 发行版、SDK、Gradle 缓存与 `java.io.tmpdir`，换机器时覆盖同名 `*_OVERRIDE` 变量即可；
跨机器/CI 的构建方式见 `BUILD.md` 第 8 节。

## 2. 纯 JVM 自测：90/90 通过

```bash
./tools/run-selftest.sh
```

三组：几何与坐标系 28 项、1 秒 tick 状态机与 Location 字段 36 项、注入参数与清理 26 项。实测输出最后三行分别是
`通过 28 项，失败 0 项`、`通过 36 项，失败 0 项`、`通过 26 项，失败 0 项`。

第一组（几何与坐标系）覆盖的内容与关键数值：

| 类别 | 断言 | 实测 |
|---|---|---|
| 距离 | 北京—上海约 1063 km（地球半径 6371000 m） | 通过（误差 < 30 km） |
| 距离 | 同一点距离为 0 | 0.0 |
| 方位 | 正北 0°、正东 90° | 0.0° / 90.0° |
| 插值 | 短线段中点两侧距离相等 | 差 < 0.05 m |
| 插值 | 长线段中点是经纬度线性中点 | 差 < 0.001 m |
| 坐标系 | 北京 WGS84→GCJ-02 偏移 | 554.9 m（合理区间 100–800 m） |
| 坐标系 | GCJ-02 往返误差 | < 0.5 m |
| 坐标系 | 北京 WGS84→BD-09 偏移 | 1374.8 m（合理区间 200–1500 m） |
| 坐标系 | BD-09 往返误差 | < 0.5 m |
| 坐标系 | 境外不做 GCJ-02 偏移（东京） | 0.0 m |
| 路线 | 构造 1000 m 线段的总长 | 1000.00 m |
| 路线 | 起点/中点/终点坐标与进度 | 0 / 0.5 / 1.0，误差 < 1 m |
| 路线 | 中点航向 | 0°（正北） |
| 路线 | 非循环越过终点 | 停在终点，`finished=true`，进度 1.0 |
| 路线 | 循环模式 1250 m 于 1000 m 环线 | 回到 250 m 处，永不 finished |
| 路线 | 过弯后航向 | 89.998°（正东） |

第二组（`SimulationEngine` + `LocationFields` + `MockState`）验证 1 秒 tick 语义：单点模式每秒原样注入且速度/方位为 0；
路线模式按真实时间差 dt 推进（首秒 10 米、被延迟后一次补齐到 30 米）；暂停时仍返回采样但注入速度为 0 且不累计位移，
继续后无跳变；非循环路线走完 `routeCompleted=true` 且 `running` 仍为 true、继续注入终点、注入速度 0；
循环路线对总长取模、永不完成；GPS 精度 3 米、network 精度 10 米、高度 0、速度精度 0.2 m/s、方位精度 2°、
垂直精度等于水平精度；状态模型进度百分比 clamp 与注入速度语义正确。

第三组（`MockInjector` + 记录型 `ProviderSink`）验证注入参数：`gps` 的 requiresSatellite=true、`network` 的
requiresNetwork=true，两者 requiresCell=false、monetaryCost=false、altitude/speed/bearing 支持全开、
powerRequirement=1、accuracy=1；未授权时拒绝写入；两条通道写入的坐标一致、精度分别 3/10 米、高度 0、
速度/方位/时间字段透传；Fused 写入失败不影响主通道；两个 provider 都失败才判定失败；cleanup 会禁用并移除两个 provider。

## 3. Robolectric Android 层测试：22/22 通过

```bash
node tools/fetch-test-deps.mjs     # 首次执行：下载并校验 android-all jar（约 350MB）
./tools/run-android-tests.sh       # 跑 :app:testDebugUnitTest
```

不需要设备或模拟器：Robolectric 在 JVM 上提供 Android 框架桩。实测 22 个用例全部通过：

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `MockEngineAndroidTest` | 3 | `AppOpsManager` 的 `android:mock_location` 判定：默认未选中、MODE_ALLOWED 选中、MODE_ERRORED 拒绝 |
| `MockLocationServiceTest` | 5 | 前台服务：单点每秒注入且坐标为下发值、gps 3 米/network 10 米精度；路线按 tick 推进、100 m/s 跑完 1000 米后 `routeCompleted` 且 `running` 仍为 true、注入速度归零；暂停期间不推进、恢复后继续；停止后清理 provider 且不再注入；未授权时不启动并给出失败原因 |
| `PlaceStoreTest` | 4 | SQLite schema（`records`、`saved_routes` 的列名逐项断言）；收藏增删改查与查重；历史保留最近 N 条；路线坐标编码与读取 |
| `MockEngineFullChainTest` | 4 | 不替换注入实现：生产 `MockEngine` → 真实 `LocationManager` API → 记录型影子，断言 addTestProvider 的 9 个参数、gps 3 米/network 10 米精度、坐标/速度/方位/时间字段、AppOps 拒绝时不写入、cleanup 的禁用与移除 |
| `MockLocationServiceRealEngineTest` | 2 | 真实服务 + 生产 `MockEngine` 的端到端链路：onStartCommand → 授权 → 建 provider → 每秒写 Location；停止后限制 provider 并停止注入 |
| `MainActivitySmokeTest` | 4 | 主界面加载（抽屉 6 项、标题、地图页）、抽屉切换到收藏页、自检对话框弹出并显示六项检查容器、debug 指令 `start_static` 发出带经纬度的 `START_MOCK` 服务 Intent |

说明：第 4、5 组测试替身了一个记录型 `LocationManager` 影子，因此「服务 → 生产注入实现 → 系统接口」整条链路
在 JVM 上也能被断言。这套测试实际发现了一个真实缺陷：服务启动路径漏掉了 `ensureTestProviders()` 调用，
真机上会表现为「开始模拟后没有任何坐标写入」；该缺陷已修复（见 `MockLocationService.checkPreconditions`）。

环境说明（实测踩过的坑，已固化进脚本与 `app/build.gradle`）：Robolectric 4.14.1 需要 API 34 与 API 35 两份
`android-all-instrumented` jar；测试 JVM 必须把 `java.io.tmpdir` 指到工程内（本机 `C:\\WINDOWS\\TEMP` 的 ACL
会让测试进程报 `AccessDeniedException`）；`OP_MOCK_LOCATION` 是隐藏常量，测试里只能用公开的
`AppOpsManager.OPSTR_MOCK_LOCATION`。

## 4. APK 静态检查

用 Android SDK 自带 `aapt2 dump badging` 检查产物：

```
package: name='io.github.mocklocation.oss' versionCode='1' versionName='0.1.0'
sdkVersion:'24'  targetSdkVersion:'34'  compileSdkVersion='34'
uses-permission: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_NETWORK_STATE,
                 INTERNET, WAKE_LOCK, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION,
                 POST_NOTIFICATIONS
application-label:'模拟定位'
launchable-activity: io.github.mocklocation.oss.MainActivity
```

APK 内容抽检：

- `assets/map.html`、`assets/leaflet.js`、`assets/leaflet.css` 与 4 个图标资源都在包内；
- dex 中确认包含 `MainActivity`、`MockLocationService`、`MapPageController`、`SelfCheckDialog`、`NominatimClient`、`PlaceListFragment`、`UiHost` 等本工程类。

## 5. 代码级一致性审计

| 审计 | 方法 | 结果 |
|---|---|---|
| JS 桥函数名 | 对比 `MapPageController` 调用的 JS 函数与 `map.html` 定义的函数 | 10 个函数两边完全一致（setTarget/clearTarget/setRoute/clearRoute/setMode/centerOn/fitRoute/setHere/clearHere/setTileLayer） |
| 格式化字符串 | 解析 strings 的占位符个数，与 `getString(R.string.x, ...)` 实参个数逐个比对 | 0 处不匹配（避免运行时 `MissingFormatArgumentException`） |
| 资源引用 | `aapt2 compile --dir res` 编译全部资源，并交叉检查 layout/menu 里的引用 | 退出码 0，无未解析引用 |
| 编译 | Gradle 全量构建 | 无错误 |

## 6. 设备端端到端验证（脚本已就绪）

`tools/device-verify.sh` 会在一台已连接 adb 的设备/模拟器上执行端到端行为验证：

1. 安装 APK（`-g` 自动授予运行时权限）；
2. 用 `appops set <pkg> android:mock_location allow` 把应用设为模拟位置应用；
3. 打开系统定位；
4. 通过 debug 专用指令入口启动单点模拟，从 `dumpsys location` 读取系统 `gps` provider 的坐标并比对；
5. 启动三点路线（36 km/h），隔 3 秒采样两次，断言坐标在移动；
6. 触发暂停，再隔 3 秒采样两次，断言坐标不再变化；
7. 停止模拟，断言 `dumpsys location` 中已无 mock location 记录；
8. 截图保存到 `tools/verify-out/`。

该入口只在 `BuildConfig.DEBUG` 构件里生效，release 包不会响应任何 `VERIFY` 指令。

**当前状态**：本机所在虚拟化环境无法运行 Android 模拟器。已实测两种加速方式都不可用：

- WHPX：`emulator -accel-check` 报告 "WHPX is installed and usable"，但模拟器进程在打印
  "Windows Hypervisor Platform accelerator is operational" 后立即退出（退出码 0x40000015）；
- TCG（`-accel off`）：qemu 进程能启动并让 `adb devices` 看到 `emulator-5554 offline`，但 guest 不执行指令：
  qemu 的 CPU 时间停在 0.48 秒不再增长，`sys.boot_completed` 永远读不到。

这是宿主嵌套虚拟化限制，不是应用缺陷。因此设备端脚本尚未产出通过记录；接上真机后执行
`./tools/device-verify.sh` 即可得到结果。

## 7. 设备走查记录

（待补充：设备型号、Android 版本、每条用例的结果与截图路径）
