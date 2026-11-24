# 功能与行为说明

本文描述 MockLocation OSS 的可观察行为，作为实现与测试的共同依据。

## 1. 启动与状态

- 入口：主界面「开始模拟」按钮。启动前校验两件事：系统已把本应用选为模拟位置应用（`AppOpsManager` 的
  `android:mock_location` 为允许），以及定位权限已授予；任一不满足则弹自检/权限申请并中止启动。
- 服务通过显式 Intent 启动，四个动作：`START_MOCK`（单点）、`START_ROUTE`（路线）、`STOP_MOCK`、`TOGGLE_ROUTE_PAUSE`。
- 状态对象（`MockState`）字段：`running`、`mode`（STATIC/ROUTE）、`paused`、`routeCompleted`、`location`、
  `speedMps`、`routeDistanceMeters`、`routeTotalMeters`、`label`、`error`；界面与服务通过 LocalBinder 订阅状态。
- 运行中 `onStartCommand` 返回 `START_REDELIVER_INTENT`，进程被杀后系统重投最后一个 Intent 以恢复模拟；未运行返回 `START_NOT_STICKY`。

## 2. 注入通道

- 建立 `gps` 与 `network` 两个测试 provider：`gps` 标记 requiresSatellite、`network` 标记 requiresNetwork；
  两者都支持 altitude/speed/bearing，powerRequirement=POWER_LOW，accuracy=ACCURACY_FINE。
- 每次写入前把 provider 状态置为 AVAILABLE，再写坐标；两个 provider 都失败才算注入失败，失败会停止服务并给出原因。
- 坐标字段：纬度、经度、altitude=0；accuracy 与所用 provider 对应（gps 3 米、network 10 米）；
  time=`System.currentTimeMillis()`、elapsedRealtimeNanos=`SystemClock.elapsedRealtimeNanos()`；bearing/speed 来自当前采样；
  Android 8.0 起额外补 verticalAccuracy=accuracy、speedAccuracy=0.2 m/s、bearingAccuracy=2°。
- 设备具备 Google Play 服务时，额外通过反射调用 `FusedLocationProviderClient` 的 `setMockMode(true)` 与
  `setMockLocation(location)`，让融合定位也返回模拟坐标；没有 GMS 时静默降级，不影响测试 provider 通道。

## 3. 1 秒 tick

- 主线程 Handler 固定 1000 ms 调度；运行中每秒注入一次。
- 单点模式：每次注入同一个坐标，速度 0、方位 0。
- 路线模式：位移 = 设定速度 × 真实经过时间差（`elapsedRealtime` 差值），因此被系统延迟后会在下一次 tick 补齐；
  暂停期间不累计位移，但仍每秒注入（速度为 0）。
- 循环路线：已行驶距离对总长取模，永不完成。非循环路线：到达终点后 `routeCompleted=true`、注入速度为 0，
  但仍在运行并持续注入终点坐标（holding final location）。
- 启动时立即注入第一帧，之后按 1 秒节拍继续；运行期间持 `PARTIAL_WAKE_LOCK`。

## 4. 路线几何

- 距离用 haversine（地球半径 6371000 米），相邻点距离累加得总长；总长 ≤ 0.01 米判无效。
- 按已行驶距离定位所在线段，并在段内按比例线性插值出坐标；方位取该段起点到终点的初始方位角。
- 路线至少两个点、速度必须大于 0；速度上限 300 km/h 由界面校验。

## 5. 通知与前台服务

- 通知渠道 importance=LOW、无声音/振动、不显示角标；通知 id 固定 1001，每秒刷新，ongoing 且不可清除。
- 标题：单点「正在模拟位置」/ 路线「正在沿路线模拟位置」；正文：单点为「名称 · 坐标」，路线为「%1$.1f km/h · %2$s」。
- 动作按钮：路线未完成时显示「暂停/继续路线」，始终有「停止模拟」；点击通知主体回到主界面。
- 前台服务类型为 `location`；Android 8+ 建通知渠道，Android 10+ 使用带类型的 `startForeground`。

## 6. 数据模型

- SQLite 库 `mock_location.db`：
  - `records(id, name, latitude, longitude, type, timestamp)`，`type` 取 `HISTORY` 或 `FAVORITE`；
  - `saved_routes(id, name, speedKmh, loop, points, updatedAt)`，`points` 为 `lat,lng;lat,lng` 文本。
- 收藏按坐标查重；历史只保留最近 200 条；路线点解析失败时跳过该段而不是崩溃。
- 设置文件 `app_settings`：`pref_map_provider`、`pref_coordinate_system`、`pref_self_check_completed`、
  `pref_review_total_success`、`pref_review_button_dismissed`。
- 搜索缓存文件 `search_cache`：键为 `query_<小写查询>`，值为结果 JSON，最多 5 条。

## 7. 坐标系

- 输入与显示支持 WGS84、GCJ-02、BD-09；注入系统时统一换算为 WGS84。
- GCJ-02 只在境内生效（经度 72.004–137.8347、纬度 0.8293–55.8271），境外原样返回；逆变换用迭代逼近，往返误差 < 0.5 米。

## 8. 地图与搜索

- 地图用打包的 Leaflet 页面加载在线瓦片；设置里可在 OSM / CARTO Voyager / OpenTopoMap 之间切换。
- 点击地图：单点模式移动目标点；路线模式追加路线点并画折线与编号点。
- 搜索：显式提交；先查本地缓存，再请求 Nominatim（HTTPS，带自定义 User-Agent），失败提示；「纬度,经度」形式的输入直接按坐标处理。
- 「我的位置」用 `LocationManager.getLastKnownLocation` 读取 GPS/网络最后位置。

## 9. 自检

六项：开发者选项、模拟位置应用（AppOps）、系统定位开关、定位权限、通知权限、电池优化白名单。
前五项为必填，电池优化为建议项；必填未通过时开始模拟会被拦截；通过后写入 `pref_self_check_completed`。
自检对话框提供跳转系统设置的入口，可重复运行。

## 10. 边界与异常

- `START_MOCK` 缺经纬度 extra：直接结束服务，不弹错误。
- 路线点数不足、速度非正、总长过短：以 `route_invalid` 文案失败并结束服务。
- 未选中模拟位置应用：界面拦截并弹自检；若绕过界面直接启动服务，服务侧同样拒绝并给出原因。
- 注入失败：记录日志、停止 tick、清理 provider、释放 WakeLock、停止前台通知，并把错误写入状态供界面展示。
- 权限被拒：请求权限并在授权后继续原操作；长期拒绝时提示去系统设置。
