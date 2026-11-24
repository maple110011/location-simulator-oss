package io.github.mocklocation.oss.core;

import java.util.List;

/**
 * 模拟定位的状态机：把「当前时间」映射成「这一秒要注入的坐标/速度/方位」。
 *
 * 这一层不依赖 Android，因此可以用普通 JVM 测试覆盖原软件最核心的 1 秒 tick 语义：
 * - 固定 1000 ms 节拍，但位移按真实时间差 dt 累计（后台被延迟时会一次补齐）；
 * - 暂停时仍 tick、仍注入，但速度为 0 且不累计位移；
 * - 循环路线用「已走距离对总长取模」，永不完成；
 * - 非循环路线走完后 routeCompleted=true、速度为 0，但 running 仍为 true（继续按住终点）。
 */
public final class SimulationEngine {

    public enum Mode { STATIC, ROUTE }

    private Mode mode = Mode.STATIC;
    private boolean running;
    private boolean paused;
    private boolean completed;
    private boolean loop;

    private GeoPoint staticPoint;
    private String label;
    private RouteEngine route;
    private double speedMps;
    private double traveledMeters;
    private long lastTickElapsedMs;

    /** 单点模拟：立即生效，之后每次 tick 注入同一个点、速度 0、方位 0。 */
    public void startStatic(GeoPoint point, String label, long nowElapsedMs) {
        this.mode = Mode.STATIC;
        this.running = true;
        this.paused = false;
        this.completed = false;
        this.loop = false;
        this.staticPoint = point;
        this.label = label;
        this.route = null;
        this.speedMps = 0.0;
        this.traveledMeters = 0.0;
        this.lastTickElapsedMs = nowElapsedMs;
    }

    /**
     * 路线模拟。
     *
     * @throws IllegalArgumentException 点数不足两点、速度非正、总长过短（与原软件判定一致）
     */
    public void startRoute(List<GeoPoint> points, double speedMps, boolean loop, long nowElapsedMs) {
        if (points == null || points.size() < 2 || speedMps <= 0.0) {
            throw new IllegalArgumentException("route or speed is invalid");
        }
        RouteEngine engine = new RouteEngine(points);
        this.mode = Mode.ROUTE;
        this.running = true;
        this.paused = false;
        this.completed = false;
        this.loop = loop;
        this.route = engine;
        this.speedMps = speedMps;
        this.traveledMeters = 0.0;
        this.staticPoint = null;
        this.label = null;
        this.lastTickElapsedMs = nowElapsedMs;
    }

    public void stop() {
        running = false;
        paused = false;
        completed = false;
        route = null;
        staticPoint = null;
        traveledMeters = 0.0;
    }

    /** 暂停/继续；只有路线模式、运行中且未完成时有效（单点模式无效）。 */
    public void togglePause(long nowElapsedMs) {
        if (!running || mode != Mode.ROUTE || completed) return;
        paused = !paused;
        lastTickElapsedMs = nowElapsedMs;
    }

    /**
     * 推进一次 tick，返回本次应当注入的采样；未运行时返回 null。
     */
    public Tick tick(long nowElapsedMs) {
        if (!running) return null;
        if (mode == Mode.STATIC) {
            if (staticPoint == null) return null;
            lastTickElapsedMs = nowElapsedMs;
            return new Tick(staticPoint, 0f, 0f, 0.0, false);
        }
        if (route == null) return null;
        double deltaSeconds = Math.max(0.0, (nowElapsedMs - lastTickElapsedMs) / 1000.0);
        lastTickElapsedMs = nowElapsedMs;
        if (!paused && !completed) {
            traveledMeters += speedMps * deltaSeconds;
        }
        double total = route.getTotalMeters();
        if (loop && total > 0.0 && traveledMeters >= total) {
            traveledMeters = traveledMeters % total;
        }
        RouteEngine.RoutePosition position = route.positionAt(traveledMeters, loop);
        completed = position.finished && !loop;
        if (!loop) {
            traveledMeters = Math.min(traveledMeters, total);
        }
        float injectedSpeed = (!paused && !completed) ? (float) speedMps : 0f;
        return new Tick(position.point, injectedSpeed, position.bearingDegrees,
                route.progress(traveledMeters), completed);
    }

    public boolean isRunning() { return running; }
    public Mode getMode() { return mode; }
    public boolean isPaused() { return paused; }
    public boolean isCompleted() { return completed; }
    public boolean isLoop() { return loop; }
    public double getSpeedMps() { return speedMps; }
    public double getTraveledMeters() { return traveledMeters; }
    public double getTotalMeters() { return route == null ? 0.0 : route.getTotalMeters(); }
    public GeoPoint getStaticPoint() { return staticPoint; }
    public String getLabel() { return label; }

    /** 一次 tick 的注入采样。 */
    public static final class Tick {
        public final GeoPoint point;
        public final float speedMps;
        public final float bearingDegrees;
        public final double progress;
        public final boolean completed;

        Tick(GeoPoint point, float speedMps, float bearingDegrees, double progress, boolean completed) {
            this.point = point;
            this.speedMps = speedMps;
            this.bearingDegrees = bearingDegrees;
            this.progress = progress;
            this.completed = completed;
        }
    }
}
