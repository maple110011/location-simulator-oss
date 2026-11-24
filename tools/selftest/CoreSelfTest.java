package io.github.mocklocation.oss.core;

import java.util.Arrays;

/** JVM 自测：不依赖 Android 与 JUnit，直接用 javac/java 跑。 */
public final class CoreSelfTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        GeoPoint beijing = new GeoPoint(39.9042, 116.4074);
        GeoPoint shanghai = new GeoPoint(31.2304, 121.4737);
        GeoPoint nearRed = new GeoPoint(39.9150, 116.4574);

        // R=6371000 时北京-上海约 1063 km（约定实现同样取 6371000）
        check("北京-上海距离约 1063km", Math.abs(GeoMath.distanceMeters(beijing, shanghai) - 1063000) < 30000);
        check("同一点距离为 0", GeoMath.distanceMeters(beijing, beijing) == 0.0);
        check("北京-近处约 4.4km", Math.abs(GeoMath.distanceMeters(beijing, nearRed) - 4400) < 400);

        GeoPoint north = new GeoPoint(beijing.latitude + 0.01, beijing.longitude);
        GeoPoint east = new GeoPoint(beijing.latitude, beijing.longitude + 0.01);
        check("正北方位角约 0", Math.abs(GeoMath.bearingDegrees(beijing, north)) < 0.5
                || Math.abs(GeoMath.bearingDegrees(beijing, north) - 360) < 0.5);
        check("正东方位角约 90", Math.abs(GeoMath.bearingDegrees(beijing, east) - 90) < 1.0);

        GeoPoint shortA = new GeoPoint(39.9000, 116.4000);
        GeoPoint shortB = new GeoPoint(39.9090, 116.4000);
        GeoPoint shortMid = GeoMath.interpolate(shortA, shortB, 0.5);
        double s1 = GeoMath.distanceMeters(shortA, shortMid);
        double s2 = GeoMath.distanceMeters(shortMid, shortB);
        check("短线段中点插值两侧距离相等", Math.abs(s1 - s2) < 0.05);
        GeoPoint mid = GeoMath.interpolate(beijing, shanghai, 0.5);
        GeoPoint expectedMid = new GeoPoint((beijing.latitude + shanghai.latitude) / 2,
                (beijing.longitude + shanghai.longitude) / 2);
        check("长线段中点是经纬度线性中点", GeoMath.distanceMeters(mid, expectedMid) < 0.001);
        check("插值 0 返回起点", GeoMath.distanceMeters(GeoMath.interpolate(beijing, shanghai, 0), beijing) < 0.01);
        check("插值 1 返回终点", GeoMath.distanceMeters(GeoMath.interpolate(beijing, shanghai, 1), shanghai) < 0.01);

        GeoPoint gcj = CoordinateConverter.wgs84ToGcj02(beijing);
        double offset = GeoMath.distanceMeters(beijing, gcj);
        System.out.println("  WGS84->GCJ02 北京偏移 = " + String.format("%.1f", offset) + " m");
        check("北京 WGS84->GCJ02 偏移在 100~800m", offset > 100 && offset < 800);
        GeoPoint back = CoordinateConverter.gcj02ToWgs84(gcj);
        check("GCJ02 往返误差 < 0.5m", GeoMath.distanceMeters(back, beijing) < 0.5);
        GeoPoint bd = CoordinateConverter.convert(beijing, CoordinateSystem.WGS84, CoordinateSystem.BD09);
        double bdOffset = GeoMath.distanceMeters(beijing, bd);
        System.out.println("  WGS84->BD09 北京偏移 = " + String.format("%.1f", bdOffset) + " m");
        check("北京 WGS84->BD09 偏移在 200~1500m", bdOffset > 200 && bdOffset < 1500);
        GeoPoint bdBack = CoordinateConverter.convert(bd, CoordinateSystem.BD09, CoordinateSystem.WGS84);
        check("BD09 往返误差 < 0.5m", GeoMath.distanceMeters(bdBack, beijing) < 0.5);
        GeoPoint tokyo = new GeoPoint(35.6812, 139.7671);
        check("境外 GCJ02 不偏移", GeoMath.distanceMeters(CoordinateConverter.wgs84ToGcj02(tokyo), tokyo) < 0.001);

        GeoPoint start = new GeoPoint(39.9000, 116.4000);
        GeoPoint end = new GeoPoint(39.9000 + 1000.0 / 111194.9, 116.4000);
        RouteEngine line = new RouteEngine(Arrays.asList(start, end));
        System.out.println("  折线总长 = " + String.format("%.2f", line.getTotalMeters()) + " m");
        check("总长约 1000 米", Math.abs(line.getTotalMeters() - 1000) < 2.0);
        RouteEngine.RoutePosition p0 = line.positionAt(0, false);
        check("起点坐标正确", GeoMath.distanceMeters(p0.point, start) < 0.5);
        check("起点进度 0", p0.progress == 0.0);
        RouteEngine.RoutePosition pMid = line.positionAt(500, false);
        check("中点距起点约 500 米", Math.abs(GeoMath.distanceMeters(pMid.point, start) - 500) < 1.0);
        check("中点进度约 0.5", Math.abs(pMid.progress - 0.5) < 0.002);
        check("中点航向约 0 度", Math.abs(pMid.bearingDegrees) < 1.0 || Math.abs(pMid.bearingDegrees - 360) < 1.0);
        check("中点未结束", !pMid.finished);
        RouteEngine.RoutePosition pEnd = line.positionAt(line.getTotalMeters(), false);
        check("终点坐标正确", GeoMath.distanceMeters(pEnd.point, end) < 0.5);
        check("终点 finished=true", pEnd.finished);
        check("终点进度 1", pEnd.progress == 1.0);
        RouteEngine.RoutePosition pOver = line.positionAt(1500, false);
        check("非循环越过终点仍停在终点", GeoMath.distanceMeters(pOver.point, end) < 0.5);
        RouteEngine.RoutePosition pLoop = line.positionAt(1250, true);
        check("循环模式 1250 米回到 250 米处", Math.abs(GeoMath.distanceMeters(pLoop.point, start) - 250) < 1.5);
        check("循环模式永不 finished", !pLoop.finished);

        GeoPoint corner = new GeoPoint(end.latitude,
                end.longitude + 500.0 / (111194.9 * Math.cos(Math.toRadians(end.latitude))));
        RouteEngine bend = new RouteEngine(Arrays.asList(start, end, corner));
        RouteEngine.RoutePosition afterTurn = bend.positionAt(bend.getTotalMeters() - 10, false);
        System.out.println("  过弯后航向 = " + afterTurn.bearingDegrees);
        check("过弯后航向指向东", Math.abs(afterTurn.bearingDegrees - 90) < 2.0);

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("[PASS] " + name); }
        else { failed++; System.out.println("[FAIL] " + name); }
    }
}
