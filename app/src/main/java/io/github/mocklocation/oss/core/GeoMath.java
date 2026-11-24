package io.github.mocklocation.oss.core;

/**
 * 球面几何：距离、方位角、折线取点。
 *
 * 与原软件（2.9.12）的实现对齐：
 * - 距离用 haversine，地球半径取 6371000 米（原实现 p092v1/c 的常量，不是 6371008.8）；
 * - 方位角用标准初始方位角公式（度，正北为 0，顺时针）；
 * - 折线取点用经纬度线性插值（原实现同样按比例线性插值，没有用球面插值），
 *   在几十公里的路线长度内两者差异远小于 GPS 精度，因此保持线性以复现原行为。
 */
public final class GeoMath {
    /** 原实现使用的地球半径，米。 */
    public static final double EARTH_RADIUS_M = 6371000.0;

    private GeoMath() {}

    private static double toRad(double deg) { return deg * Math.PI / 180.0; }
    private static double toDeg(double rad) { return rad * 180.0 / Math.PI; }

    public static double distanceMeters(GeoPoint a, GeoPoint b) {
        return distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude);
    }

    /** 两点间大圆距离，米。 */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = toRad(lat2 - lat1);
        double dLon = toRad(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** 从 a 指向 b 的初始方位角，度数 [0,360)。两点重合时返回 0。 */
    public static double bearingDegrees(GeoPoint a, GeoPoint b) {
        double lat1 = toRad(a.latitude);
        double lat2 = toRad(b.latitude);
        double dLon = toRad(b.longitude - a.longitude);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        if (y == 0.0 && x == 0.0) return 0.0;
        return (toDeg(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    /** 经纬度线性插值，fraction 按 [0,1] 截断。 */
    public static GeoPoint interpolate(GeoPoint a, GeoPoint b, double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        return new GeoPoint(a.latitude + (b.latitude - a.latitude) * f,
                a.longitude + (b.longitude - a.longitude) * f);
    }
}
