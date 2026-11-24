#!/bin/sh
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

commit "chore: 初始化 Android 工程骨架与可复现构建脚本" \
  .gitignore build.gradle settings.gradle gradle.properties build-debug.sh app/build.gradle app/proguard-rules.pro

commit "feat(core): 球面几何与路线引擎

- GeoMath：haversine 距离（R=6371000）、初始方位角、经纬度线性插值
- RouteEngine：折线累计距离、按距离取点、循环取模、非循环终点保持、进度" \
  app/src/main/java/io/github/mocklocation/oss/core/GeoPoint.java \
  app/src/main/java/io/github/mocklocation/oss/core/GeoMath.java \
  app/src/main/java/io/github/mocklocation/oss/core/RouteEngine.java

commit "feat(core): WGS84/GCJ-02/BD-09 坐标换算" \
  app/src/main/java/io/github/mocklocation/oss/core/CoordinateSystem.java \
  app/src/main/java/io/github/mocklocation/oss/core/CoordinateConverter.java

commit "feat(core): 1 秒 tick 状态机与 Location 字段

- SimulationEngine：单点/路线、按真实时间差推进、暂停不推进、循环取模、完成后保持终点
- LocationFields/MockState：注入字段与可观察状态" \
  app/src/main/java/io/github/mocklocation/oss/core/SimulationEngine.java \
  app/src/main/java/io/github/mocklocation/oss/core/LocationFields.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockState.java

commit "feat(core): 注入编排与 Android provider 适配

- ProviderSink/ProviderSpec/MockAuthorizer/TimeSource/FusedMockWriter 抽象系统能力，便于 JVM 验证
- MockInjector 负责 addTestProvider 参数与每秒写入；MockEngine 接 AppOps、LocationManager 与 GMS Fused mock（反射）" \
  app/src/main/java/io/github/mocklocation/oss/core/ProviderSpec.java \
  app/src/main/java/io/github/mocklocation/oss/core/ProviderSink.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockAuthorizer.java \
  app/src/main/java/io/github/mocklocation/oss/core/TimeSource.java \
  app/src/main/java/io/github/mocklocation/oss/core/FusedMockWriter.java \
  app/src/main/java/io/github/mocklocation/oss/core/AndroidLocationProviderSink.java \
  app/src/main/java/io/github/mocklocation/oss/core/AndroidMockAuthorizer.java \
  app/src/main/java/io/github/mocklocation/oss/core/AndroidTimeSource.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockInjector.java \
  app/src/main/java/io/github/mocklocation/oss/core/MockEngine.java

commit "feat(service): 前台模拟服务（1 秒 tick/通知/暂停/停止）

- 四个 action、START_REDELIVER_INTENT、PARTIAL_WAKE_LOCK、前台通知与暂停/停止按钮
- LocalBinder 发布 MockState；debug 构件提供 VERIFY 指令入口供自动化验收" \
  app/src/main/java/io/github/mocklocation/oss/service/MockLocationService.java

commit "feat(data): SQLite 记录/路线/设置持久化" \
  app/src/main/java/io/github/mocklocation/oss/data/PlaceDbHelper.java \
  app/src/main/java/io/github/mocklocation/oss/data/PlaceStore.java \
  app/src/main/java/io/github/mocklocation/oss/data/SettingsStore.java \
  app/src/main/java/io/github/mocklocation/oss/data/SearchCacheStore.java

commit "feat(ui): 地图选点、路线规划、收藏历史路线、设置与自检" \
  app/src/main/java/io/github/mocklocation/oss/MainActivity.java \
  app/src/main/java/io/github/mocklocation/oss/ui

commit "feat(res): 布局、图标、字符串与 Leaflet 地图资源" \
  app/src/main/res app/src/main/assets

commit "feat: AndroidManifest 权限与前台服务声明" \
  app/src/main/AndroidManifest.xml

commit "test: 纯 JVM 自测（90 项）与 Robolectric Android 层测试（12 项）

- tools/selftest：几何 28 + 状态机 36 + 注入 26
- app/src/test：AppOps 判定、服务 tick、SQLite schema 与 CRUD
- tools/device-verify.sh：真机端到端断言脚本" \
  tools app/src/test

commit "build: 构建可移植化与 GitHub Actions 流水线

- 签名与 Robolectric 依赖目录可用 Gradle 属性/环境变量覆盖
- CI 在 ubuntu 上构建并跑三组测试、上传 APK" \
  .github .gitignore app/build.gradle tools/fetch-test-deps.mjs tools/run-android-tests.sh

commit "docs: 功能说明、架构说明、构建与验证文档" \
  docs README.md BUILD.md VERIFY.md LICENSE

echo '--- commits ---'
git log --oneline
echo "tracked: $(git ls-files | wc -l)"
