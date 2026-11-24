package io.github.mocklocation.oss.core;

/**
 * 模拟运行状态，是服务与 UI 之间唯一的状态契约。
 *
 * 字段与原软件 L0.d（MockState）一一对应：
 * isRunning / mode(STATIC|ROUTE) / isPaused / routeCompleted / currentLocation /
 * speedMetersPerSecond / routeDistanceMeters / routeTotalMeters / errorMessage。
 */
public final class MockState {
    public enum Mode { STATIC, ROUTE }

    public final boolean running;
    public final Mode mode;
    public final boolean paused;
    /** 非循环路线是否已经走完；走完之后 running 仍为 true，每秒继续注入终点。 */
    public final boolean routeCompleted;
    public final GeoPoint location;
    /** 米/秒；单点模式为 0，暂停或走完时注入速度也为 0。 */
    public final double speedMps;
    public final double routeDistanceMeters;
    public final double routeTotalMeters;
    /** 用户给地点起的名字，可为 null。 */
    public final String label;
    /** 失败原因，成功时为 null。 */
    public final String error;

    private MockState(boolean running, Mode mode, boolean paused, boolean routeCompleted, GeoPoint location,
                      double speedMps, double routeDistanceMeters, double routeTotalMeters,
                      String label, String error) {
        this.running = running;
        this.mode = mode;
        this.paused = paused;
        this.routeCompleted = routeCompleted;
        this.location = location;
        this.speedMps = speedMps;
        this.routeDistanceMeters = routeDistanceMeters;
        this.routeTotalMeters = routeTotalMeters;
        this.label = label;
        this.error = error;
    }

    public static MockState idle() {
        return new MockState(false, Mode.STATIC, false, false, null, 0.0, 0.0, 0.0, null, null);
    }

    public static MockState failed(String error) {
        return new MockState(false, Mode.STATIC, false, false, null, 0.0, 0.0, 0.0, null, error);
    }

    public static MockState staticLocation(GeoPoint point, String label) {
        return new MockState(true, Mode.STATIC, false, false, point, 0.0, 0.0, 0.0, label, null);
    }

    public static MockState route(double speedMps, GeoPoint point, double traveled, double total,
                                  boolean paused, boolean completed) {
        return new MockState(true, Mode.ROUTE, paused, completed, point, speedMps, traveled, total, null, null);
    }

    public MockState withProgress(GeoPoint point, double traveled, boolean paused, boolean completed) {
        return new MockState(running, mode, paused, completed, point, speedMps, traveled, routeTotalMeters, label, error);
    }

    public MockState withPaused(boolean nextPaused) {
        return new MockState(running, mode, nextPaused, routeCompleted, location, speedMps,
                routeDistanceMeters, routeTotalMeters, label, error);
    }

    /** 进度百分比，clamp 到 0..100，与原软件 UI 一致。 */
    public int progressPercent() {
        if (routeTotalMeters <= 0.0) return 0;
        double pct = routeDistanceMeters / routeTotalMeters * 100.0;
        return (int) Math.max(0, Math.min(100, Math.round(pct)));
    }

    /** 通知与 UI 用的「速度 km/h」。 */
    public double speedKmh() { return speedMps * 3.6; }

    /** 注入系统时应使用的速度：暂停、完成、单点都是 0。 */
    public float injectedSpeedMps() {
        if (!running || paused || routeCompleted || mode == Mode.STATIC) return 0f;
        return (float) speedMps;
    }
}
