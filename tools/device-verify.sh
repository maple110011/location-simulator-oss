#!/bin/sh
# ============================================================================
# 设备/模拟器上的自动化行为验收：验证「模拟定位」真的生效，而不只是编译通过。
#
# 前置：一台已连接（adb devices 可见）并允许安装应用的 Android 7.0+ 设备或模拟器。
# 用法：./tools/device-verify.sh [apk 路径]
#
# 步骤：
#   1. 安装 debug APK（-g 自动授予运行时权限）；
#   2. 用 appops 把应用设为「模拟位置应用」（等价于开发者选项里手动选择）；
#   3. 打开系统定位开关；
#   4. 通过 debug 专用指令入口 io.github.mocklocation.oss.VERIFY 启动单点模拟，
#      从 dumpsys location 读取系统 gps provider 的坐标，与下发坐标比对；
#   5. 启动一条 3 点路线，隔 3 秒采样两次，确认坐标沿着路线移动；
#   6. 暂停路线，再隔 3 秒采样两次，确认坐标不再变化；
#   7. 停止模拟，确认测试 provider 已移除；
#   8. 截图保存到 tools/verify-out/。
#
# 退出码：0 全部通过；1 有断言失败；2 环境/参数错误。
# ============================================================================
set -u
cd "$(dirname "$0")/.."

ADB="${ADB:-E:/software/ss/Deepseek-Harness/build-tools/android-sdk/platform-tools/adb.exe}"
PKG="io.github.mocklocation.oss"
ACTION="io.github.mocklocation.oss.VERIFY"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="tools/verify-out"
FAILED=0

[ -x "$ADB" ] || [ -f "$ADB" ] || { echo "找不到 adb：$ADB" >&2; exit 2; }
[ -f "$APK" ] || { echo "找不到 APK：$APK" >&2; exit 2; }
mkdir -p "$OUT"
rm -f "$OUT"/*.txt "$OUT"/*.png 2>/dev/null || true

say()  { printf '%s\n' "$*"; }
pass() { printf '  [PASS] %s\n' "$*"; }
fail() { printf '  [FAIL] %s\n' "$*"; FAILED=1; }
adbs() { "$ADB" shell "$@" 2>&1 | tr -d '\r'; }

# 从 dumpsys location 里取指定 provider 的最后一个坐标
last_location() {
  adbs dumpsys location | grep -o "Location\\[$1 [0-9.-]*,[0-9.-]*" | head -1 | sed "s/.*\\[//"
}

say "== 0. 等待设备 =="
"$ADB" wait-for-device || { echo "没有可用设备" >&2; exit 2; }
MODEL=$(adbs getprop ro.product.model)
VERSION=$(adbs getprop ro.build.version.release)
say "设备：$MODEL / Android $VERSION"

say "== 1. 安装 APK =="
"$ADB" install -r -g "$APK" > "$OUT/install.txt" 2>&1 && pass "安装成功" || { fail "安装失败"; cat "$OUT/install.txt"; }

say "== 2. 授权为模拟位置应用 =="
adbs appops set "$PKG" android:mock_location allow > /dev/null 2>&1 || adbs appops set "$PKG" MOCK_LOCATION allow > /dev/null 2>&1 || true
APPOP=$(adbs appops get "$PKG" android:mock_location)
case "$APPOP" in
  *allow*) pass "AppOps android:mock_location = allow" ;;
  *) fail "AppOps 未生效（$APPOP）" ;;
esac

say "== 3. 打开系统定位 =="
adbs cmd location set-location-enabled true > /dev/null 2>&1 || adbs settings put secure location_mode 3 > /dev/null 2>&1 || true
say "  定位模式：$(adbs settings get secure location_mode)"

say "== 4. 单点模拟 =="
adbs am force-stop "$PKG"
LAT="39.904200"; LNG="116.407400"
adbs am start -W -a "$ACTION" -n "$PKG/.MainActivity" --es command start_static --es point "$LAT,$LNG" > "$OUT/start-static.txt" 2>&1
sleep 4
adbs dumpsys location > "$OUT/location-static.txt"
STATIC_GPS=$(last_location gps)
say "  gps provider 最后位置：$STATIC_GPS"
case "$STATIC_GPS" in
  39.9042*,116.4074*) pass "gps provider 的坐标与下发坐标一致" ;;
  *) fail "gps provider 未拿到下发坐标" ;;
esac

say "== 5. 路线模拟 =="
ROUTE="39.904200,116.407400;39.905100,116.407400;39.906000,116.407400"
adbs am start -W -a "$ACTION" -n "$PKG/.MainActivity" --es command start_route --es route "$ROUTE" --ef speed_kmh 36 > "$OUT/start-route.txt" 2>&1
sleep 3
ROUTE_A=$(last_location gps)
sleep 3
ROUTE_B=$(last_location gps)
say "  路线采样 A：$ROUTE_A"
say "  路线采样 B：$ROUTE_B"
if [ -n "$ROUTE_A" ] && [ -n "$ROUTE_B" ] && [ "$ROUTE_A" != "$ROUTE_B" ]; then
  pass "路线模拟期间坐标在移动"
else
  fail "路线模拟期间坐标没有变化"
fi

say "== 6. 暂停路线 =="
adbs am start -W -a "$ACTION" -n "$PKG/.MainActivity" --es command toggle_pause > "$OUT/pause.txt" 2>&1
sleep 2
PAUSE_A=$(last_location gps)
sleep 3
PAUSE_B=$(last_location gps)
say "  暂停采样 A：$PAUSE_A"
say "  暂停采样 B：$PAUSE_B"
if [ -n "$PAUSE_A" ] && [ "$PAUSE_A" = "$PAUSE_B" ]; then
  pass "暂停后坐标不再变化"
else
  fail "暂停后坐标仍在变化"
fi

say "== 7. 停止模拟 =="
adbs am start -W -a "$ACTION" -n "$PKG/.MainActivity" --es command stop > "$OUT/stop.txt" 2>&1
sleep 3
adbs dumpsys location > "$OUT/location-stopped.txt"
if grep -q "mock location" "$OUT/location-stopped.txt" 2>/dev/null; then
  fail "停止后仍存在 mock location 记录（需人工确认）"
else
  pass "停止后 dumpsys location 中没有 mock location 记录"
fi

say "== 8. 截图 =="
"$ADB" exec-out screencap -p > "$OUT/screen-after-verify.png" 2>/dev/null && pass "截图：$OUT/screen-after-verify.png" || say "  截图失败（不影响结论）"

say ""
if [ "$FAILED" -eq 0 ]; then
  say "结果：全部通过"
else
  say "结果：存在失败项，详见 $OUT/"
fi
exit "$FAILED"
