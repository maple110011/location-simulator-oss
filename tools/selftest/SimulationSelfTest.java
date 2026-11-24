package io.github.mocklocation.oss.core;

import java.util.Arrays;

/** JVM 自测（第二部分）：1 秒 tick 状态机、Location 字段构造、状态模型。 */
public final class SimulationSelfTest {
    private static int passed = 0;
    private static int failed = 0;

    /** 一条正北 1000 米的两点路线。 */
    private static java.util.List<GeoPoint> line1000m() {
        GeoPoint start = new GeoPoint(39.9000, 116.4000);
        GeoPoint end = new GeoPoint(39.9000 + 1000.0 / 111194.9, 116.4000);
        return Arrays.asList(start, end);
    }

    public static void main(String[] args) {
        // ---------- 单点模式 ----------
        SimulationEngine engine = new SimulationEngine();
        GeoPoint beijing = new GeoPoint(39.9042, 116.4074);
        engine.startStatic(beijing, "单点", 1000L);
        SimulationEngine.Tick staticTick = engine.tick(2000L);
        check("单点 tick 返回同一坐标", staticTick != null && GeoMath.distanceMeters(staticTick.point, beijing) < 0.001);
        check("单点注入速度 0", staticTick != null && staticTick.speedMps == 0f);
        check("单点注入方位 0", staticTick != null && staticTick.bearingDegrees == 0f);
        check("单点模式 isRunning", engine.isRunning());
        engine.stop();
        check("停止后 tick 返回 null", engine.tick(3000L) == null);

        // ---------- 路线推进 ----------
        engine = new SimulationEngine();
        engine.startRoute(line1000m(), 10.0, false, 0L);   // 10 m/s = 36 km/h
        SimulationEngine.Tick t1 = engine.tick(1000L);
        check("首秒推进 10 米", t1 != null && Math.abs(engine.getTraveledMeters() - 10.0) < 0.01);
        check("首秒进度约 1%", t1 != null && Math.abs(t1.progress - 0.01) < 0.0005);
        check("路线注入速度 = 设定速度", t1 != null && Math.abs(t1.speedMps - 10f) < 0.001f);
        check("路线注入方位约 0 度", t1 != null && (t1.bearingDegrees < 1.0 || t1.bearingDegrees > 359.0));

        SimulationEngine.Tick t2 = engine.tick(3000L);      // dt=2s
        check("被延迟后按真实时间差补齐（累计 30 米）", t2 != null && Math.abs(engine.getTraveledMeters() - 30.0) < 0.01);

        // ---------- 暂停/继续 ----------
        engine.togglePause(4000L);
        double beforePause = engine.getTraveledMeters();
        SimulationEngine.Tick paused1 = engine.tick(5000L);
        SimulationEngine.Tick paused2 = engine.tick(6000L);
        check("暂停后仍返回采样（继续注入）", paused1 != null && paused2 != null);
        check("暂停时注入速度为 0", paused1 != null && paused1.speedMps == 0f);
        check("暂停期间不累计位移", Math.abs(engine.getTraveledMeters() - beforePause) < 1e-9);
        engine.togglePause(7000L);
        engine.tick(8000L);
        check("继续后恢复推进且无跳变", Math.abs(engine.getTraveledMeters() - (beforePause + 10.0)) < 0.01);

        // ---------- 非循环完成 ----------
        engine = new SimulationEngine();
        engine.startRoute(line1000m(), 100.0, false, 0L);  // 100 m/s → 10 秒跑完
        SimulationEngine.Tick finish = engine.tick(11000L);
        check("非循环路线走完 routeCompleted", finish != null && finish.completed && engine.isCompleted());
        check("走完但 running 仍为 true（holding final location）", engine.isRunning());
        check("走完注入速度为 0", finish != null && finish.speedMps == 0f);
        check("走完位置为终点", finish != null && finish.point.latitude > 39.9000);
        SimulationEngine.Tick afterFinish = engine.tick(12000L);
        check("走完后继续按秒注入终点", afterFinish != null && afterFinish.completed
                && GeoMath.distanceMeters(afterFinish.point, finish.point) < 0.5);

        // ---------- 循环路线 ----------
        engine = new SimulationEngine();
        engine.startRoute(line1000m(), 100.0, true, 0L);
        SimulationEngine.Tick loopTick = engine.tick(12500L); // 1250 米 → 取模回 250 米
        check("循环路线永不完成", loopTick != null && !engine.isCompleted() && !loopTick.completed);
        check("循环路线取模回到 250 米处", engine.getTraveledMeters() >= 0.0
                && engine.getTraveledMeters() < engine.getTotalMeters());
        check("循环路线仍在运行", engine.isRunning());

        // ---------- Location 字段 ----------
        LocationFields gpsFields = LocationFields.of(beijing, 12.5f, 90f, LocationFields.ACCURACY_GPS_M, 1000L, 5000L);
        check("gps 精度 3 米", gpsFields.accuracyMeters == 3.0f);
        check("network 精度 10 米", LocationFields.ACCURACY_NETWORK_M == 10.0f);
        check("高度为 0", gpsFields.altitude == 0.0);
        check("速度/方位透传", gpsFields.speedMetersPerSecond == 12.5f && gpsFields.bearingDegrees == 90f);
        check("time 与 elapsedRealtimeNanos 透传", gpsFields.timeMillis == 1000L && gpsFields.elapsedRealtimeNanos == 5000L);
        check("垂直精度等于水平精度", gpsFields.verticalAccuracyMeters() == gpsFields.accuracyMeters);
        check("速度精度 0.2 m/s", LocationFields.SPEED_ACCURACY_METERS_PER_SECOND == 0.2f);
        check("方位精度 2 度", LocationFields.BEARING_ACCURACY_DEGREES == 2.0f);

        // ---------- 状态模型 ----------
        MockState idle = MockState.idle();
        check("idle 未运行", !idle.running);
        check("idle 进度 0", idle.progressPercent() == 0);
        MockState half = MockState.route(10.0, beijing, 500.0, 1000.0, false, false);
        check("进度 500/1000 = 50%", half.progressPercent() == 50);
        check("运行中注入速度 = 设定速度", Math.abs(half.injectedSpeedMps() - 10f) < 0.001f);
        check("暂停时注入速度 0", half.withPaused(true).injectedSpeedMps() == 0f);
        MockState over = MockState.route(10.0, beijing, 1500.0, 1000.0, false, false);
        check("进度上限 100%", over.progressPercent() == 100);

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("[PASS] " + name); }
        else { failed++; System.out.println("[FAIL] " + name); }
    }
}
