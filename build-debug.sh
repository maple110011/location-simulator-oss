#!/bin/sh
# ============================================================================
# mock-location-oss 本机可复现构建脚本（Windows + Git/MSYS bash）
#
# 用法：在本目录执行 ./build-debug.sh（等价于 :app:assembleDebug --offline）
#
# 为什么不用 build-tools/gradle-8.2/bin/gradle：
#   该启动脚本在 MSYS 下执行 cygpath --path --mixed "$APP_HOME" 时，
#   会把 Windows 盘符路径 "E:/..." 误当成路径列表，改写成 "E;/..."，
#   导致 JVM 收到 -javaagent:E;/... 而启动失败：
#     Error opening zip file or JAR manifest missing : E;/...
#     Error occurred during initialization of VM / agent library failed Agent_OnLoad
#   因此这里直接用 JDK 的 java 启动 GradleMain，路径一律用 E:/... 形式。
#
# 环境变量（可用同名 *OVERRIDE 变量替换，用于换机器）：
#   JAVA_HOME_OVERRIDE / GRADLE_HOME_OVERRIDE / GRADLE_USER_HOME_OVERRIDE / ANDROID_USER_HOME_OVERRIDE
# ============================================================================
set -eu

# 工程目录：POSIX 路径用于 cd，Windows 路径（E:/...）用于传给 AGP。
cd "$(dirname "$0")"
PROJECT_DIR=$PWD
PROJECT_DIR_WIN=$(cygpath --mixed "$PROJECT_DIR" 2>/dev/null || printf %s "$PROJECT_DIR")

JAVA_HOME="${JAVA_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/jdk-extract/jdk-21.0.12.1+1}"
GRADLE_HOME="${GRADLE_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/gradle-8.2}"
GRADLE_USER_HOME="${GRADLE_USER_HOME_OVERRIDE:-E:/software/ss/Deepseek-Harness/build-tools/gradle-home}"
export GRADLE_USER_HOME

# AGP 会读写 ~/.android；本机 HOME 目录处于沙箱只读状态（无法创建 ~/.android），
# 因此把 Android 用户目录重定向到工程内。注意必须传 Windows 风格路径，
# 传 POSIX 路径 /e/... 会被 AGP 解析成 "E:\e" 并报错。
ANDROID_USER_HOME="${ANDROID_USER_HOME_OVERRIDE:-$PROJECT_DIR_WIN/.android-home}"
export ANDROID_USER_HOME
mkdir -p "$ANDROID_USER_HOME"

# 当前 shell 默认 JAVA_HOME 指向只含 JRE 1.8 的目录，CLASSPATH 也指向 jre-1.8/lib；
# 不清掉会把 JVM 带进坏状态（JAVA_HOME 已被上面覆盖）。
unset CLASSPATH JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS 2>/dev/null || true

# 本机 C:\WINDOWS\TEMP 上的历史临时目录可能带着不可写的 ACL，AGP 的 MergeJavaRes
# 会在那里建 tempdir_* 而报 AccessDeniedException；把 JVM 临时目录固定到工程内。
TMP_DIR_WIN="$PROJECT_DIR_WIN/.tmp"
mkdir -p "$TMP_DIR_WIN"
export TMPDIR="$TMP_DIR_WIN"

exec "$JAVA_HOME/bin/java" \
  -Xmx64m -Xms64m -Dfile.encoding=UTF-8 "-Djava.io.tmpdir=$TMP_DIR_WIN" \
  "-javaagent:$GRADLE_HOME/lib/agents/gradle-instrumentation-agent-8.2.jar" \
  -Dorg.gradle.appname=gradle \
  -classpath "$GRADLE_HOME/lib/gradle-launcher-8.2.jar" \
  org.gradle.launcher.GradleMain \
  :app:assembleDebug --offline --console=plain "$@"
