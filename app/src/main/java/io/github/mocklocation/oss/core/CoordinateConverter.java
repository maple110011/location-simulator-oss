package io.github.mocklocation.oss.core;

/**
 * WGS84 / GCJ-02 / BD-09 互转。
 *
 * 原软件调用百度 SDK 的 CoordinateConverter 完成换算；复刻版不依赖百度 SDK，因此把公开算法自己实现一遍。
 * 约定：注入系统的坐标一律是 WGS84，坐标系的区别只体现在输入框、地址显示和地图 SDK 边界。
 */
public final class CoordinateConverter {
    private static final double PI = Math.PI;
    private static final double A = 6378245.0;            // 克拉索夫斯基椭球长半轴
    private static final double EE = 0.00669342162296594323; // 偏心率平方
    private static final double X_PI = PI * 3000.0 / 180.0;

    private CoordinateConverter() {}

    /** GCJ-02 只在国境内生效，境外按原样返回。 */
    public static boolean outOfChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    public static GeoPoint wgs84ToGcj02(GeoPoint p) {
        if (outOfChina(p.latitude, p.longitude)) return p;
        double[] d = delta(p.latitude, p.longitude);
        return new GeoPoint(p.latitude + d[0], p.longitude + d[1]);
    }

    /** GCJ-02 逆变换用迭代逼近，误差可收敛到厘米级。 */
    public static GeoPoint gcj02ToWgs84(GeoPoint p) {
        if (outOfChina(p.latitude, p.longitude)) return p;
        GeoPoint guess = p;
        for (int i = 0; i < 30; i++) {
            GeoPoint forward = wgs84ToGcj02(guess);
            double dLat = forward.latitude - p.latitude;
            double dLon = forward.longitude - p.longitude;
            if (Math.abs(dLat) < 1e-9 && Math.abs(dLon) < 1e-9) break;
            guess = new GeoPoint(guess.latitude - dLat, guess.longitude - dLon);
        }
        return guess;
    }

    public static GeoPoint gcj02ToBd09(GeoPoint p) {
        double z = Math.sqrt(p.longitude * p.longitude + p.latitude * p.latitude) + 0.00002 * Math.sin(p.latitude * X_PI);
        double theta = Math.atan2(p.latitude, p.longitude) + 0.000003 * Math.cos(p.longitude * X_PI);
        return new GeoPoint(z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065);
    }

    public static GeoPoint bd09ToGcj02(GeoPoint p) {
        double x = p.longitude - 0.0065;
        double y = p.latitude - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        return new GeoPoint(z * Math.sin(theta), z * Math.cos(theta));
    }

    public static GeoPoint convert(GeoPoint p, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) return p;
        // 先统一到 WGS84，再转到目标，避免三种组合各写一套。
        GeoPoint wgs;
        switch (from) {
            case WGS84: wgs = p; break;
            case GCJ02: wgs = gcj02ToWgs84(p); break;
            case BD09: wgs = gcj02ToWgs84(bd09ToGcj02(p)); break;
            default: throw new IllegalArgumentException("unknown system: " + from);
        }
        switch (to) {
            case WGS84: return wgs;
            case GCJ02: return wgs84ToGcj02(wgs);
            case BD09: return gcj02ToBd09(wgs84ToGcj02(wgs));
            default: throw new IllegalArgumentException("unknown system: " + to);
        }
    }

    private static double[] delta(double lat, double lon) {
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{dLat, dLon};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
