# 位置模拟器（com.yangtools.gps.simulator 2.9.12）逆向与开源复刻

本项目要回答两个问题：第一，`位置模拟器/base.apk` 这个软件到底怎样把手机的定位改成用户指定的位置；第二，我们怎样写一个开源程序，把它的功能完整复刻出来。

## 1. 分析对象

- 安装包：`E:\software\ss\Deepseek-Harness\位置模拟器\base.apk`（11,244,643 字节，单一 `classes.dex`，5,260,724 字节，无 `lib/` 原生库）。
- 包信息（来自反编译后的 `resources/AndroidManifest.xml`）：包名 `com.yangtools.gps.simulator`，versionCode 53，versionName 2.9.12，minSdkVersion 23，targetSdkVersion 36，compileSdkVersion 36。
- 自有代码包名：`com.example.mocklocation`（清单里声明的组件名没有被混淆），混淆只发生在第三方与工具类上。
- 关键权限：`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`ACCESS_MOCK_LOCATION`、`WRITE_SECURE_SETTINGS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_LOCATION`、`POST_NOTIFICATIONS`、`WAKE_LOCK`、`com.android.vending.BILLING`。
- 关键组件：`com.example.mocklocation.MainActivity`（启动页）、`FeedbackActivity`、`service.MockLocationService`（`foregroundServiceType="location"`）。
- 第三方：百度地图 SDK（`com.baidu.lbsapi.API_KEY`）、Google Maps API key、Firebase（Firestore/RemoteConfig/Installations）、Google Play Billing 8.0.0、PairIP 授权校验（`com.pairip.licensecheck`）、Room 数据库 `mock_location.db`。

## 2. 本次使用的逆向工具（已安装）

| 工具 | 版本 | 安装位置 | 用途 |
|---|---|---|---|
| leanrea | 8.2 KB 单文件 SKILL.md（github.com/aicodeking/leanrea） | 项目级 `./.agents/skills/leanrea/SKILL.md`（已生效）；源码副本在 `re-tools/leanrea/` | 提供 APK 逆向的最短命令路径与陷阱清单，本次 jadx 用法按该 skill 的 Android 行执行 |
| REA (rea-agents) | 6.7.0（github.com/morluto/rea） | 本地依赖安装于 `re-tools/rea-install/`（`node_modules/rea-agents`），MCP 补丁文件 `re-tools/rea.cordis.yml` | 提供 MCP/CLI 逆向工具集（`decode-android-resources`、`decompile`、`analyze` 等），本次主要用其 CLI 能力清单与 MCP 握手验证 |
| jadx | 1.5.6 | `re-tools/mvn/lib`（44 个 jar，由 `re-tools/mvn-resolve.mjs` 从 Maven Central 解析） | 反编译 DEX 到 Java，输出 `re-tools/jadx-out/` |
| OpenJDK | 17.0.2 | `re-tools/jdk17/jdk-17.0.2` | 运行 jadx（jadx 1.5.x 要求 Java 11+，本机原有 Java 8 不够用） |

安装过程中的环境事实（供复现）：本机 `github.com` 直连超时、`schannel` 在沙箱内取不到 TLS 凭据，因此 GitHub 源码用 Node `fetch` 从 `codeload.github.com`/`api.github.com` 下载，Maven 依赖从 `repo1.maven.org` 解析；npm 走 `registry.npmmirror.com` 镜像并把 `npm_config_cache` 指到工作区内。REA 的 MCP 注册需要写 `E:\dsh\cordis.patch.yml`（工作区之外），本会话沙箱拒绝了该写入且审批超时，因此只交付了可直接使用的补丁文件，启用方法见第 4 节。

## 3. 目录

- `spec/01-模拟定位实现机制.md`：这个 App 如何骗过系统和其它应用，完整机制与证据。
- `spec/02-复刻功能范围与差异.md`：要复刻的功能清单、验收标准，以及因私有 key/SDK 不能原样复刻的部分。
- `analyst/`：对界面、交互、搜索、坐标系、存储、自检、订阅等逐项的行为规格（子代理产出）。
- `../mock-location-oss/`：开源复刻工程（Android，Java）。
- `../re-tools/`：逆向工具与反编译产物（jadx-out 为源码树）。

## 4. 启用 REA MCP（可选，需要一次工作区外写入）

DSH 的 MCP 通过 Cordis 补丁层注册。把 `re-tools/rea.cordis.yml` 的内容作为一条 `insert` 补丁合并进 `E:\dsh\cordis.patch.yml`（该文件当前结尾是空列表 `[]`，需要用补丁列表替换它），或者启动时显式加载：

```bash
dsh web --patch "E:/software/ss/Deepseek-Harness/re-tools/rea.cordis.yml"
```

重启后模型侧会出现 `mcp__rea__*` 系列工具。REA 的 stdio 握手已用 `initialize` 报文验证通过（serverInfo name=rea, version=6.7.0）。

## 5. 当前进度（2026-11-24 第 1 轮）

已完成：

- 逆向工具安装：leanrea 技能（项目级已生效）、REA 6.7.0（本地依赖 + MCP 补丁文件）、jadx 1.5.6 运行环境（Maven Central 解析 44 个 jar + OpenJDK 17）。
- APK 反编译：`re-tools/jadx-out/`（sources 3400 个类、resources 完整可读），20 个反编译错误不影响自有代码。
- 机制分析：`spec/01-模拟定位实现机制.md` 已给出结论并标注证据。
- 复刻方案：`spec/02-开源复刻方案.md` 给出功能对齐表、模块划分、差异说明与验收标准。
- 核心引擎代码（已通过 27 项 JVM 自测）：`staging/core/` 下 `GeoPoint`、`GeoMath`、`CoordinateConverter`（WGS84/GCJ-02/BD-09 互转，往返误差 < 0.5 米）、`RouteEngine`（累计距离、插值、循环、进度、结束语义）、`MockState`、`MockEngine`（LocationManager 双 provider 注入 + GMS 反射 mock + AppOps 检测）、`MockLocationService`（1 秒 tick、路线推进、前台通知、暂停/停止、WakeLock、START_REDELIVER_INTENT）、数据层（`PlaceDbHelper`/`PlaceStore`/`SettingsStore`）。
- 地图资产：`staging/assets/` 下的 `map.html`（Leaflet + OSM/CARTO/OpenTopo 瓦片、点击回调、目标点、路线编号点与折线）与 leaflet.js/css。

进行中（子代理）：

- `apk-analyst`：完整界面/交互/搜索/坐标系/存储/自检/订阅行为规格 → `analyst/`。
- `build-smith`：Android 工程骨架与构建链验证 → `../mock-location-oss/`。
- `ui-dev`：UI 层 Java + XML 实现 → `staging/ui/`（完成后由 lead 集成）。

下一步：合并 staging 到 `mock-location-oss/`，构建出可安装 APK，然后按 `spec/02` 的验收清单做设备走查。
