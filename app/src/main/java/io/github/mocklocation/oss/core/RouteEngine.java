package io.github.mocklocation.oss.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 路线模拟的推进器：把「已行驶距离」映射成折线上的坐标、航向与进度。
 *
 * 与原始实现一致：启动时一次性累计每段长度；每个 tick 用「速度 × 时间」增加已行驶距离；
 * 到达终点后，循环模式取模回到起点，非循环模式停在终点并保持最后一次坐标。
 */
public final class RouteEngine {
    /** 路线很短时视为无效，与原软件的 0.01 米阈值一致。 */
    public static final double MIN_TOTAL_METERS = 0.01;

    private final List<GeoPoint> points;
    private final List<Double> segmentMeters = new ArrayList<>();
    private final double totalMeters;

    public RouteEngine(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("route needs at least two points");
        }
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        double sum = 0.0;
        for (int i = 1; i < points.size(); i++) {
            double d = GeoMath.distanceMeters(points.get(i - 1), points.get(i));
            segmentMeters.add(d);
            sum += d;
        }
        this.totalMeters = sum;
        if (totalMeters <= MIN_TOTAL_METERS) {
            throw new IllegalArgumentException("route total distance is too short");
        }
    }

    public List<GeoPoint> getPoints() { return points; }

    public double getTotalMeters() { return totalMeters; }

    /** 0..1 的进度。超过终点按 1 处理。 */
    public double progress(double traveledMeters) {
        return Math.max(0.0, Math.min(1.0, traveledMeters / totalMeters));
    }

    /**
     * 取路线上的位置快照。
     *
     * @param traveledMeters 已行驶距离（米）；循环模式下内部取模，非循环模式下截断在终点。
     */
    public RoutePosition positionAt(double traveledMeters, boolean loop) {
        double d = traveledMeters;
        if (loop) {
            d = ((d % totalMeters) + totalMeters) % totalMeters;
        } else if (d >= totalMeters - 1e-6) {
            GeoPoint last = points.get(points.size() - 1);
            GeoPoint prev = points.get(points.size() - 2);
            return new RoutePosition(last, (float) GeoMath.bearingDegrees(prev, last), 1.0, true);
        }
        if (d < 0.0) d = 0.0;
        double acc = 0.0;
        for (int i = 0; i < segmentMeters.size(); i++) {
            double seg = segmentMeters.get(i);
            if (acc + seg >= d || i == segmentMeters.size() - 1) {
                double fraction = seg <= 0.0 ? 0.0 : (d - acc) / seg;
                GeoPoint a = points.get(i), b = points.get(i + 1);
                GeoPoint p = GeoMath.interpolate(a, b, fraction);
                return new RoutePosition(p, (float) GeoMath.bearingDegrees(a, b), progress(d), false);
            }
            acc += seg;
        }
        // 理论不可达：循环保证会在上面返回。
        return new RoutePosition(points.get(points.size() - 1), 0f, 1.0, true);
    }

    /** 路线上的一个位置快照。 */
    public static final class RoutePosition {
        public final GeoPoint point;
        public final float bearingDegrees;
        public final double progress;
        /** 非循环路线是否已经跑完（此时坐标固定为终点）。 */
        public final boolean finished;

        RoutePosition(GeoPoint point, float bearingDegrees, double progress, boolean finished) {
            this.point = point;
            this.bearingDegrees = bearingDegrees;
            this.progress = progress;
            this.finished = finished;
        }
    }
}
