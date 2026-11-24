#!/bin/sh
# ============================================================================
# 核心引擎 JVM 自测（不依赖 Android、不依赖 Gradle、不依赖 JUnit）
#
#   1) CoreSelfTest            距离/方位/插值/坐标系/折线几何（28 项）
#   2) SimulationSelfTest      1 秒 tick 状态机 / Location 字段 / 状态模型（36 项）
#   3) MockEngineInjectionTest 注入参数（双 provider/精度/字段）与清理（26 项）
#
# 用法：在工程根目录执行 ./tools/run-selftest.sh
# 退出码：0 全部通过；非 0 表示有失败或环境问题。
# ============================================================================
set -eu
cd "$(dirname "$0")/.."

JDK="${JDK_HOME:-E:/software/ss/Deepseek-Harness/build-tools/jdk-extract/jdk-21.0.12.1+1}"
if [ ! -x "$JDK/bin/javac" ]; then
  echo "找不到 JDK：$JDK（可用 JDK_HOME=... 覆盖）" >&2
  exit 2
fi

SRC=app/src/main/java/io/github/mocklocation/oss
OUT=tools/.selftest-out
rm -rf "$OUT"
mkdir -p "$OUT"

# 纯 Java 核心类（MockEngine 依赖 android.*，不参与 JVM 自测）
"$JDK/bin/javac" -encoding UTF-8 -d "$OUT" \
  "$SRC/core/GeoPoint.java" \
  "$SRC/core/GeoMath.java" \
  "$SRC/core/CoordinateSystem.java" \
  "$SRC/core/CoordinateConverter.java" \
  "$SRC/core/RouteEngine.java" \
  "$SRC/core/MockState.java" \
  "$SRC/core/LocationFields.java" \
  "$SRC/core/SimulationEngine.java" \
  "$SRC/core/ProviderSpec.java" \
  "$SRC/core/ProviderSink.java" \
  "$SRC/core/MockAuthorizer.java" \
  "$SRC/core/TimeSource.java" \
  "$SRC/core/AndroidLocationProviderSink.java" \
  "$SRC/core/MockEngine.java" \
  tools/selftest/CoreSelfTest.java \
  tools/selftest/SimulationSelfTest.java \
  tools/selftest/MockEngineInjectionTest.java

echo "==== 1/3 几何与坐标系 ===="
"$JDK/bin/java" -Dstdout.encoding=UTF-8 -cp "$OUT" io.github.mocklocation.oss.core.CoreSelfTest
echo
echo "==== 2/3 模拟状态机与 Location 字段 ===="
"$JDK/bin/java" -Dstdout.encoding=UTF-8 -cp "$OUT" io.github.mocklocation.oss.core.SimulationSelfTest
echo
echo "==== 3/3 MockEngine 注入参数与采样 ===="
"$JDK/bin/java" -Dstdout.encoding=UTF-8 -cp "$OUT" io.github.mocklocation.oss.core.MockEngineInjectionTest
