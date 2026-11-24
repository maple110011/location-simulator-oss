#!/bin/sh
# 运行 Robolectric Android 层单元测试（不需要设备/模拟器）。
# 前置：node tools/fetch-test-deps.mjs 已把 android-all jar 放到 robolectric-deps 目录。
set -eu
cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/jdk-extract/jdk-21.0.12.1+1}"
GRADLE_HOME="${GRADLE_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/gradle-8.2}"
GRADLE_USER_HOME="${GRADLE_USER_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/gradle-home}"
ANDROID_USER_HOME="${ANDROID_USER_HOME_OVERRIDE:-$PWD/.android-home}"
export GRADLE_USER_HOME ANDROID_USER_HOME
unset CLASSPATH JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS 2>/dev/null || true

"$JAVA_HOME/bin/java" -Xmx64m -Dfile.encoding=UTF-8 \
  "-Djava.io.tmpdir=$PWD/.tmp" \
  -Dorg.gradle.appname=gradle \
  -classpath "$GRADLE_HOME/lib/gradle-launcher-8.2.jar" \
  org.gradle.launcher.GradleMain :app:testDebugUnitTest --console=plain "$@"
