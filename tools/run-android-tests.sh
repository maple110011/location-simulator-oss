#!/bin/sh
# 运行 Robolectric Android 层单元测试（不需要设备/模拟器）。
# 前置：node tools/fetch-test-deps.mjs 已把 android-all jar 放到 robolectric-deps 目录。
set -eu
cd "$(dirname "$0")/.."

# Java / AGP 都是 Windows 程序，所有传给它们的路径必须是 Windows 风格（E:/...）；
# MSYS 的 /e/... 会被解释成当前盘符相对路径，导致 "E:\e" 之类的错误。
PROJECT_WIN=$(cygpath -m "$PWD" 2>/dev/null || printf %s "$PWD")
mkdir -p "$PWD/.tmp"

JAVA_HOME="${JAVA_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/jdk-extract/jdk-21.0.12.1+1}"
GRADLE_HOME="${GRADLE_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/gradle-8.2}"
GRADLE_USER_HOME="${GRADLE_USER_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/gradle-home}"
ANDROID_USER_HOME="${ANDROID_USER_HOME_OVERRIDE:-$PROJECT_WIN/.android-home}"
export GRADLE_USER_HOME ANDROID_USER_HOME
mkdir -p "$ANDROID_USER_HOME"
unset CLASSPATH JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS 2>/dev/null || true

# Robolectric android-all jar：优先用本机既有目录，否则用工程内 .robolectric-deps。
# 通过 Gradle 属性传递（而不是环境变量），避免复用 daemon 时读到陈旧环境。
ROBO_DEPS="$PROJECT_WIN/.robolectric-deps"
if [ -d "/e/software/ss/Deepseek-Harness/build-tools/robolectric-deps" ]; then
  ROBO_DEPS="E:/software/ss/Deepseek-Harness/build-tools/robolectric-deps"
fi

"$JAVA_HOME/bin/java" -Xmx64m -Dfile.encoding=UTF-8 \
  "-Djava.io.tmpdir=$PROJECT_WIN/.tmp" \
  -Dorg.gradle.appname=gradle \
  -classpath "$GRADLE_HOME/lib/gradle-launcher-8.2.jar" \
  org.gradle.launcher.GradleMain :app:testDebugUnitTest \
  "-ProbolectricDepsDir=$ROBO_DEPS" --console=plain "$@"
