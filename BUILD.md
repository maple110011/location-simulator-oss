# mock-location-oss 构建说明（BUILD.md）

本文记录「本应用」开源实现工程骨架（Android / Java）在本机 Windows + MSYS bash 环境下的
工具链、可复现构建命令、产物校验结果，以及搭建过程中实际遇到的环境问题与解决办法。

## 1. 交付物

| 项目 | 路径 / 值 |
| --- | --- |
| 工程根目录 | `E:/software/ss/Deepseek-Harness/mock-location-oss/` |
| 构建脚本 | `E:/software/ss/Deepseek-Harness/mock-location-oss/build-debug.sh` |
| APK 产物 | `E:/software/ss/Deepseek-Harness/mock-location-oss/app/build/outputs/apk/debug/app-debug.apk` |
| APK 大小 | 5,188,340 字节（约 4.95 MiB） |
| APK SHA-256 | `c5034434550d1aa2a151e5fc54db123aab772c9b0ea08e9556bd2a3fef27da6b` |
| 构建结果 | `BUILD SUCCESSFUL`，30 个 task 全部执行/复用成功 |

两次从零全量构建产出的 APK 的 SHA-256 完全一致，说明在本机同一工具链下构建可复现。

## 2. 本机工具链（均已验证存在，无需联网下载）

| 组件 | 路径 | 版本 / 说明 |
| --- | --- | --- |
| JDK | `E:/software/ss/Deepseek-Harness/build-tools/jdk-extract/jdk-21.0.12.1+1` | Eclipse Temurin 21.0.12.1+1（AGP 8.2 要求 JDK 17+） |
| Gradle 发行版 | `E:/software/ss/Deepseek-Harness/build-tools/gradle-8.2` | Gradle 8.2（已解压，不使用 wrapper，避免联网下载） |
| Android SDK | `E:/software/ss/Deepseek-Harness/build-tools/android-sdk` | `platforms/android-34`、`build-tools/34.0.0`、`platform-tools` |
| Gradle 依赖缓存 | `E:/software/ss/Deepseek-Harness/build-tools/gradle-home` | 作为 `GRADLE_USER_HOME`，内含 AGP 8.2.0 与本次全部依赖 |
| debug 签名库 | `E:/software/ss/Deepseek-Harness/build-tools/debug.keystore` | 别名 `androiddebugkey`，口令 `android` |

已确认缓存的依赖版本：`com.android.tools.build:gradle:8.2.0`、`androidx.appcompat:appcompat:1.6.1`、
`com.google.android.material:material:1.11.0`、`androidx.constraintlayout:constraintlayout:2.1.4`。

## 3. 工程配置摘要

- 包名 / namespace：`io.github.mocklocation.oss`，versionCode 1，versionName 0.1.0。
- `compileSdk 34`、`targetSdk 34`、`minSdk 24`，Java 8 语法（`sourceCompatibility/targetCompatibility = 1.8`）。
- 依赖只有三个：appcompat 1.6.1、material 1.11.0、constraintlayout 2.1.4。
  未使用 Room、地图 SDK/Google 地图 SDK、云端配置 等缓存中不存在的库。
- 仓库配置在 `settings.gradle`：aliyun 镜像（public/central/google/gradle-plugin）优先，
  `google()`、`mavenCentral()` 兜底。离线构建时仓库配置不生效，全部走 `gradle-home` 缓存。
- `local.properties` 指定 `sdk.dir=E:/software/ss/Deepseek-Harness/build-tools/android-sdk`。
- 骨架组件：
  - `MainActivity`：WebView 占位地图（本地 HTML 网格，不联网、不接地图 SDK）+ 4 个按钮
    （开始模拟 / 停止模拟 / 保存记录 / 统计记录）。
  - `service/MockLocationService`：前台服务骨架，含通知渠道、常驻通知、`ACTION_START/ACTION_STOP`；
    真正的 mock location 注入逻辑按需求留空。
  - `data/LocationDbHelper`：`SQLiteOpenHelper`，数据库 `location.db`，表 `record`
    （`id/lat/lng/name/created_at`），提供插入与计数两个方法。
- Manifest 权限：`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`FOREGROUND_SERVICE`、
  `FOREGROUND_SERVICE_LOCATION`、`POST_NOTIFICATIONS`、`INTERNET`；
  `MockLocationService` 声明 `android:foregroundServiceType="location"`。

## 4. 可复现构建命令（推荐做法）

在本机 MSYS bash（DSH 的 ops_bash 即是）中执行：

```bash
cd /e/software/ss/Deepseek-Harness/mock-location-oss
./build-debug.sh
```

成功时末尾输出 `BUILD SUCCESSFUL in ...`，产物路径为
`app/build/outputs/apk/debug/app-debug.apk`。

校验产物：

```bash
cd /e/software/ss/Deepseek-Harness/mock-location-oss
/e/software/ss/Deepseek-Harness/build-tools/android-sdk/build-tools/34.0.0/aapt.exe \
  dump badging app/build/outputs/apk/debug/app-debug.apk | head -20
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

`build-debug.sh` 内部做了四件事（脚本里也写了注释，便于以后接手）：
1. 把 `JAVA_HOME` 固定为上面那个 JDK 21（覆盖当前 shell 默认的 JRE 1.8），
   并把 `GRADLE_USER_HOME` 指向本机缓存目录；
2. 把 `ANDROID_USER_HOME` 重定向到工程内的 `.android-home`（原因见第 6 节坑 3）；
3. 清掉 `CLASSPATH`、`JAVA_TOOL_OPTIONS`、`_JAVA_OPTIONS`、`JDK_JAVA_OPTIONS`，
   避免把坏参数带进 JVM（原因见第 6 节坑 1）；
4. 不经过 `build-tools/gradle-8.2/bin/gradle` 这个 shell 启动器，而是直接用
   `$JAVA_HOME/bin/java` 启动 `org.gradle.launcher.GradleMain`（原因见第 6 节坑 2），
   最终执行的 Gradle 任务是 `:app:assembleDebug --offline --console=plain`。

## 5. 手动等价命令（脚本内部实际执行的形式）

如果不想用脚本，可以逐条执行下面这段（与脚本等价；把最后的 `--offline` 去掉即为联网解析）：

```bash
cd /e/software/ss/Deepseek-Harness/mock-location-oss
export JAVA_HOME="E:/software/ss/Deepseek-Harness/build-tools/jdk-extract/jdk-21.0.12.1+1"
export GRADLE_USER_HOME="E:/software/ss/Deepseek-Harness/build-tools/gradle-home"
export ANDROID_USER_HOME="E:/software/ss/Deepseek-Harness/mock-location-oss/.android-home"
mkdir -p "$ANDROID_USER_HOME"
unset CLASSPATH JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS 2>/dev/null

GH="E:/software/ss/Deepseek-Harness/build-tools/gradle-8.2"
"$JAVA_HOME/bin/java" -Xmx64m -Xms64m -Dfile.encoding=UTF-8 \
  "-javaagent:$GH/lib/agents/gradle-instrumentation-agent-8.2.jar" \
  -Dorg.gradle.appname=gradle \
  -classpath "$GH/lib/gradle-launcher-8.2.jar" \
  org.gradle.launcher.GradleMain :app:assembleDebug --offline --console=plain
```

注意：本机用 `bin/gradle` 启动器会失败（见坑 2），不要用
`../build-tools/gradle-8.2/bin/gradle :app:assembleDebug --offline` 这条命令作为标准构建方式。

## 6. 实际遇到的环境坑与解法

### 坑 1：默认 JAVA_HOME 指向 JRE 1.8，且 CLASSPATH 指向 jre-1.8/lib

- 现象：直接运行 Gradle 可能被带进坏 JVM 状态；默认 `JAVA_HOME=C:\Program Files\Java`
  （只有 JRE 1.8），`CLASSPATH=C:\Program Files\Java\jre-1.8\lib`。
- 原因：AGP 8.2 要求 JDK 17+ 才能运行，JRE 8 不满足；指向 jre-1.8/lib 的 CLASSPATH 会干扰 JVM。
- 解法：显式 `export JAVA_HOME=<JDK21 路径>`，并 `unset CLASSPATH JAVA_TOOL_OPTIONS
  _JAVA_OPTIONS JDK_JAVA_OPTIONS`。

### 坑 2：MSYS 下 `bin/gradle` 启动器把 Windows 盘符路径改写成 `E;/`，JVM 起不来

- 现象（本机实测，清空工程缓存后稳定复现）：

  ```
  Error opening zip file or JAR manifest missing :
    E;/software/ss/Deepseek-Harness/build-tools/gradle-8.2/lib/agents/gradle-instrumentation-agent-8.2.jar
  Error occurred during initialization of VM
  agent library failed Agent_OnLoad: instrument
  ```

- 原因：Gradle 8.2 的 `bin/gradle` shell 启动器在 MSYS/(Git) bash 下会执行
  `APP_HOME=$( cygpath --path --mixed "$APP_HOME" )`（脚本第 171 行附近）。本机环境的 `cygpath`
  把 Windows 盘符路径 `E:/...` 当成「路径列表」，把盘符冒号 `:` 当作列表分隔符，
  输出成 `E;/software/...`；该字符串随后被拼进 `-javaagent:`，JVM 便找不到 agent jar
  并在初始化阶段退出。手工执行 `cygpath --path --mixed "/e/software/ss/Deepseek-Harness"`
  可复现同样的 `E;/...` 输出。
- 解法：不经过该 shell 启动器，直接用 `java` 加 `-javaagent/-classpath` 参数启动
  `org.gradle.launcher.GradleMain`（即 `build-debug.sh` 的做法），路径全部使用 `E:/...` 形式。
  另需注意：直接用 `java` 时传 `-javaagent:E:/...` 是正常的，实测不会被改写。

### 坑 3：HOME 目录不可写，AGP 应用插件时访问 `~/.android` 失败

- 现象：直接构建时报

  ```
  Failed to apply plugin 'com.android.internal.application'.
     > C:\Users\<用户名>\.android
  ```

  （本机用户名含中文「小新air15」，日志里这个路径会因控制台编码显示成乱码。）
- 原因：本会话文件沙箱只允许写工作区（`E:/software/ss/Deepseek-Harness`），
  用户主目录下的 `C:\Users\<用户名>\.android` 无法创建；AGP 在应用 `com.android.application`
  插件时需要这个 Android 用户目录。
- 解法：设置 `ANDROID_USER_HOME` 指向工程内目录（`mock-location-oss/.android-home`，
  脚本里已 `mkdir -p`）。
- 补充坑：`ANDROID_USER_HOME` 必须传 Windows 风格路径（`E:/.../.android-home`）。
  传 POSIX 风格（`/e/.../.android-home`）时 AGP 会把它解析成错误路径并报 `> E:\e`；
  这一点实测对比过：同一命令 POSIX 路径构建失败、Windows 路径构建成功。

### 坑 4：中文用户名导致日志乱码（不影响构建）

- 现象：javac 输出显示为乱码，例如 `Դֵ 8 �ѹ�ʱ`、用户名显示为 `С??air15`。
- 原因：Windows 控制台与 JVM 默认编码（GBK）同 UTF-8 不一致。
- 解法：在 `gradle.properties` 的 `org.gradle.jvmargs` 中追加
  `-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8`；
  这只是显示问题，不影响构建结果。

### 坑 5：Java 8 编译选项的过时警告

- 现象：`compileDebugJavaWithJavac` 输出「源值 8 已过时」「目标值 8 已过时」。
- 原因：JDK 21 的 javac 对 `-source 8 -target 8` 给出 deprecation 提示。
- 处理：这是任务要求的 Java 8 语法设置，属于预期警告，不阻断构建，保持现状。

### 坑 6：不要用 Gradle wrapper / 不要联网下载工具链

- 本机 `gradle-home` 已缓存 AGP 与全部依赖，因此统一加 `--offline` 构建，避免走网络。
- 工程内没有放 `gradlew`/wrapper，避免首次构建触发 Gradle 发行版下载。
- 如果换到缓存不全的机器：去掉 `--offline` 即可，`settings.gradle` 已把 aliyun 镜像放在前面。

### 坑 7：debug 签名不依赖 `~/.android/debug.keystore`

- 由于 `~/.android` 在本机不可写，`app/build.gradle` 的 `signingConfigs.debug` 显式指向
  `build-tools/debug.keystore`，避免 AGP 尝试在用户主目录自动生成 debug keystore。

## 7. 已验证的构建结果与耗时

- 产物校验（`aapt dump badging` 实测）：

  ```
  package: name='io.github.mocklocation.oss' versionCode='1' versionName='0.1.0'
           compileSdkVersion='34'
  sdkVersion:'24'
  targetSdkVersion:'34'
  uses-permission: ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
  uses-permission: FOREGROUND_SERVICE / FOREGROUND_SERVICE_LOCATION
  uses-permission: POST_NOTIFICATIONS / INTERNET
  launchable-activity: io.github.mocklocation.oss.MainActivity
  ```

  另外 `aapt dump xmltree` 确认 `MockLocationService` 的
  `android:foregroundServiceType` 值为 `0x8`（即 location）。

- 耗时（本机 `./build-debug.sh`）：
  - 首次冷启动全量构建（清空 `app/build`、`.gradle` 后，Gradle daemon 与构建缓存均未预热）：约 43~51 秒；
  - daemon 预热后的全量构建（同样清空 `app/build`、`.gradle`）：约 20 秒；
  - 增量构建（无改动重跑）：约 7 秒，30 个 task 全部 `up-to-date`。

## 8. 从零复现步骤

1. 确认第 2 节的 5 个路径都存在（JDK、Gradle 发行版、SDK、gradle-home 缓存、debug.keystore）。
2. 确认 `local.properties` 里的 `sdk.dir` 指向本机 SDK。
3. 在 MSYS bash 中执行 `cd /e/software/ss/Deepseek-Harness/mock-location-oss && ./build-debug.sh`。
4. 检查 `app/build/outputs/apk/debug/app-debug.apk` 是否存在，并用第 4 节的 `aapt dump badging` 校验包名/权限。
5. 如需换机器/换目录：通过 `JAVA_HOME_OVERRIDE`、`GRADLE_HOME_OVERRIDE`、
   `GRADLE_USER_HOME_OVERRIDE`、`ANDROID_USER_HOME_OVERRIDE` 覆盖脚本里的默认路径，
   并同步修改 `local.properties` 的 `sdk.dir`。

## 9. 已知遗留问题 / 限制

1. **`bin/gradle` 启动器在本机 MSYS 下不可靠**（坑 2）：清空工程缓存后必现失败，
   已有缓存时可能偶然成功，所以标准构建方式统一用 `build-debug.sh`。若以后改用
   Android Studio，AS 不经过这个 bash 启动器，不受该问题影响。
2. **前台服务通知在 Android 13+ 需要运行时授权 `POST_NOTIFICATIONS`**：Manifest 已声明，
   但骨架代码尚未做运行时权限申请；这不影响构建，只是通知可能不显示。
3. **模拟定位注入逻辑未实现**：`MockLocationService` 只是前台服务骨架（通知 + 生命周期），
   没有实现 `LocationManager.setTestProviderLocation`，也没有做开发者选项 / 模拟位置 AppOps 引导。
4. **仅在 Windows 10 19045 + 上述工具链验证**，其他系统/版本未验证。

## 8. 在其他机器 / CI 上构建

本机默认路径只影响调试签名与 Robolectric 测试依赖，源码本身不依赖这些路径。可用 Gradle 属性或环境变量覆盖：

| 用途 | Gradle 属性 | 环境变量 | 未设置时的默认值 |
|---|---|---|---|
| 调试签名 keystore | `-PmockLocationKeystore=...` | `MOCK_LOCATION_KEYSTORE` | `E:/software/ss/Deepseek-Harness/build-tools/debug.keystore`；文件不存在时改用 AGP 自动生成的 debug keystore |
| Robolectric android-all jar 目录 | `-ProbolectricDepsDir=...` | `ROBOLECTRIC_DEPS_DIR` | `<工程根>/.robolectric-deps`（已 gitignore） |

CI（`.github/workflows/ci.yml`）在 ubuntu-latest 上执行：JDK 21 + Android SDK + Gradle 8.2 →
`tools/run-selftest.sh`（90 项纯 JVM 自测）→ `tools/fetch-test-deps.mjs`（约 350MB 的 android-all jar）→
`gradle :app:assembleDebug :app:testDebugUnitTest` → 上传 APK 与测试报告。

注意：`build-debug.sh` 是本机专用脚本（固定了本机工具链并强制 `--offline`），跨机器构建请直接用
`gradle :app:assembleDebug`，或改脚本顶部的 `*_OVERRIDE` 变量。
