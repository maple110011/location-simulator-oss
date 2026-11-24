# 架构与设计取舍

## 1. 分层

```
UI (MainActivity / ui/*)                 只收集输入、展示状态
  ↓  Intent(action) / LocalBinder
Service (service/MockLocationService)    1 秒 tick、通知、WakeLock、生命周期
  ↓  tick 结果
Core (core/SimulationEngine)             纯 Java 状态机：单点/路线/暂停/循环/完成
  ↓  采样（坐标/速度/方位）
Core (core/MockInjector)                 纯 Java 编排：provider 建立/清理、每秒写入
  ↓  ProviderSink
Android (core/AndroidLocationProviderSink, core/MockEngine, GMS 反射)
```

## 2. 为什么把状态机与注入编排做成纯 Java

Android 的 mock provider 能力（`addTestProvider`、`setTestProviderLocation`）在 JVM 单元测试框架里没有完整影子实现，
如果逻辑直接写在 Service 里，就只能靠真机验证。因此：

- `SimulationEngine` 不引用任何 Android 类型，tick 语义（真实时间差、暂停、循环、完成保持）用普通 JUnit 风格的
  main 方法即可覆盖（`tools/selftest/SimulationSelfTest`）。
- `MockInjector` 把系统写入抽象成 `ProviderSink`、把授权判定抽象成 `MockAuthorizer`、把时间抽象成 `TimeSource`，
  于是「provider 参数、gps 3 米/network 10 米精度、每秒写入字段」可以在 JVM 上用记录型 sink 断言
  （`tools/selftest/MockEngineInjectionTest`）。

`MockEngine` 是唯一直接接触 `LocationManager`/`AppOpsManager`/GMS 的类，保持很薄。

## 3. GMS Fused mock 用反射

如果在构建期依赖 `play-services-location`，没有 GMS 的设备/构建环境会引入体积与可用性问题。
因此本应用只在运行时探测 `com.google.android.gms.location.LocationServices`，存在则反射调用
`setMockMode` / `setMockLocation`，不存在就跳过。失败只影响融合定位通道，不影响测试 provider。

## 4. 数据层用 SQLiteOpenHelper 而非 Room

Room 需要额外的注解处理器与运行时依赖，而本项目的数据模型很简单（两张表 + 若干查询）。
用 `SQLiteOpenHelper` 手写建表与 DAO，可以把依赖压到最小，同时让表结构在代码里一眼可读、可测试
（`app/src/test/.../PlaceStoreTest` 直接断言列名）。

## 5. 地图片用打包的 Leaflet + 在线瓦片

- 不引入商业地图 SDK：它们需要私有 API key、会显著增大 APK，并且部分 SDK 要求设备具备 Google Play 服务。
- Leaflet 只有约 160KB，打包在 `assets/`，地图交互（点击取点、折线、编号点、瓦片切换）通过 `JavascriptInterface` 桥接。
- 瓦片与地址搜索都走 OpenStreetMap 公开服务，因此项目本身不携带任何密钥。

## 6. 可测试性与 debug 验证入口

`MainActivity` 在 debug 构件里响应 `io.github.mocklocation.oss.VERIFY` 动作的 Intent
（`start_static` / `start_route` / `toggle_pause` / `stop`），让 `tools/device-verify.sh` 可以在真机上自动驱动
模拟流程并用 `dumpsys location` 断言结果。release 构件不响应这些指令（`BuildConfig.DEBUG` 判断）。

## 7. 依赖注入与测试 seam

- `MockLocationService.createMockEngine()` 是 `protected`，Robolectric 测试用子类替换成记录型引擎；
- 服务通过 `Listener` 发布 `MockState`，界面与测试共用同一状态契约；
- 测试用 `robolectric.properties` 固定 SDK 版本，`app/build.gradle` 把测试 JVM 的临时目录固定到工程内。
