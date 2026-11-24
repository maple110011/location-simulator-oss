#!/bin/sh
# 把工程整理成一组小而可回退的提交（用户要求：小步快跑）
set -eu
cd "$(dirname "$0")/.."
rm -rf .git
git init -q
git branch -M main

commit() {
  msg="$1"; shift
  git add -- "$@"
  git -c user.name="mock-location-oss" -c user.email="noreply@example.com" commit -q -m "$msg"
}

commit "chore: Android 工程骨架与可复现构建脚本

- Gradle 8.2 + AGP 8.2 + compileSdk 34，离线依赖缓存来自 build-tools/gradle-home
- build-debug.sh 固定 JDK/Gradle/SDK/临时目录/调试签名，一条命令产出 debug APK" \
  .gitignore build.gradle settings.gradle gradle.properties build-debug.sh app/build.gradle app/proguard-rules.pro

commit "feat(core): 球面几何与路线引擎

- GeoPoint/GeoMath：haversine 距离（R=6371000）、初始方位角、线性插值（与原实现一致）
- RouteEngine：折线累计距离、按距离取点、循环取模、非循环终点保持、进度" \
  app/src/main/java/io/github/mocklocation/oss/core/GeoPoint.java \
  app/src/main/java/io/github/mocklocation/oss/core/GeoMath.java \
  app/src/main/java/io/github/mocklocation/oss/core/RouteEngine.java

commit "feat(core): WGS84/GCJ-02/BD-09 坐标换算

- CoordinateSystem 枚举与 CoordinateConverter：境内判定、GCJ-02 迭代逆变换、BD-09 极坐标偏移
- 约定注入系统一律 WGS84，坐标系只影响输入与显示（与原软件一致）" \
  app/src/main/java/io/github/mocklocation/oss/core/CoordinateSystem.java \
  app/src/main/java/io/github/mocklocation/oss/core/CoordinateConverter.java

commit "feat(core): 1 秒 tick 状态机与 Location 字段

- SimulationEngine：单点/路线、按真实时间差推进、暂停不推进、循环取模、完成后保持终点
- LocationFields/MockState：注入字段与原软件一致（gps 3 米/network 10 米、高度 0、扩展精度）" \
  app/src/main/java/io/github/mocklocation/oss/core/SimulationEngine.java \
  app/src/main/java/io/github/mocklocation/oss/core/LocationFields.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockState.java

commit "feat(core): 注入编排与 Android provider 适配

- ProviderSink/ProviderSpec/MockAuthorizer/TimeSource 把系统能力抽象出来，便于 JVM 验证
- MockInjector 负责 addTestProvider 参数与每秒写入；MockEngine 接 AppOps、LocationManager、GMS Fused mock（反射）" \
  app/src/main/java/io/github/mocklocation/oss/core/ProviderSpec.java \
  app/src/main/java/io/github/mocklocation/oss/core/ProviderSink.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockAuthorizer.java \
  app/src/main/java/io/github/mocklocation/oss/core/TimeSource.java \
  app/src/main/java/io/github/mocklocation/oss/core/AndroidLocationProviderSink.java \
  app/src/main/java/io/github/mocklocation/oss/core/AndroidMockAuthorizer.java \
  app/src/main/java/io/github/mocklocation/oss/core/AndroidTimeSource.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockInjector.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockEngine.java

commit "feat(service): 前台模拟服务（1 秒 tick/通知/暂停/停止）

- 四个 action、START_REDELIVER_INTENT、PARTIAL_WAKE_LOCK、前台通知与暂停/停止按钮
- 通过 LocalBinder 向界面发布 MockState；debug 构件提供 VERIFY 指令入口供自动化验收" \
  app/src/main/java/io/github/mocklocation/oss/service/MockLocationService.java

commit "feat(data): SQLite 记录/路线/设置持久化

- records(id,name,latitude,longitude,type,timestamp) 与 saved_routes(...) 对齐原软件 Room 建表
- PlaceStore 提供收藏/历史/路线 CRUD；SettingsStore/SearchCacheStore 沿用原软件键名" \
  app/src/main/java/io/github/mocklocation/oss/data/PlaceDbHelper.java \
  app/src/main/java/io/github/mocklocation/oss/data/PlaceStore.java \
  app/src/main/java/io/github/mocklocation/oss/data/SettingsStore.java \
  app/src/main/java/io/github/mocklocation/oss/data/SearchCacheStore.java

commit "feat(ui): 地图选点、路线规划、收藏历史路线、设置与自检

- MainActivity + 抽屉六页；地图页 Leaflet 交互；坐标输入；Nominatim 搜索（带本地缓存）
- SelfCheckDialog 六项检查；开始模拟前校验模拟位置应用与定位权限" \
  app/src/main/java/io/github/mocklocation/oss/MainActivity.java \
  app/src/main/java/io/github/mocklocation/oss/ui

commit "feat(res): 布局、图标、字符串与 Leaflet 地图资源" \
  app/src/main/res app/src/main/assets

commit "feat: AndroidManifest 权限与前台服务声明" \
  app/src/main/AndroidManifest.xml

commit "test: 90 项 JVM 自测、Robolectric 测试与设备验收脚本

- tools/selftest：几何 28 + 状态机 36 + 注入参数 26
- tools/device-verify.sh：appops/dumpsys location 端到端断言
- app/src/test：Robolectric 覆盖 AppOps 判定、服务 tick、SQLite schema 与 CRUD" \
  tools app/src/test

commit "docs: 逆向分析档案、复刻对照表、构建与验证说明

- docs/analysis：原软件逐项行为规格（第三方 API key 已脱敏）
- docs/复刻对照表.md：行为→实现→验证逐项对照；BUILD.md/VERIFY.md/README.md" \
  docs README.md BUILD.md VERIFY.md LICENSE .gitignore

echo '--- commits ---'
git log --oneline
echo "tracked: $(git ls-files | wc -l)"
