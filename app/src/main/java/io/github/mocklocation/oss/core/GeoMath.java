package io.github.mocklocation.oss.core;

/**
 * 球面几何：距离、方位角、折线取点。
 *
 * 设计约定：
 * - 距离用 haversine，地球半径取 6371000 米；
 * - 方位角用标准大圆初始方位角公式（度，正北为 0，顺时针）；
 * - 折线取点用经纬度线性插值：在几十公里的路线长度内，线性插值与球面插值的差异远小于 GPS 精度。
 */
public final class GeoMath {
    /** 使用的地球半径，米。 */
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
