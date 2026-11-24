package io.github.mocklocation.oss.core;

/**
 * 一次注入要写进 android.location.Location 的全部字段，纯 Java 数据对象，便于在 JVM 上验证。
 *
 * 约定：altitude=0、accuracy 由 provider 决定（gps 3 米 / network 10 米）、
 * time 与 elapsedRealtimeNanos 取注入时刻、bearing/speed 来自路线采样；
 * Android 8.0 起额外补 verticalAccuracy=accuracy、speedAccuracy=0.2 m/s、bearingAccuracy=2°。
 */
public final class LocationFields {
    /** 注入精度约定：gps 3 米、network 10 米。 */
    public static final float ACCURACY_GPS_M = 3.0f;
    public static final float ACCURACY_NETWORK_M = 10.0f;
    public static final float SPEED_ACCURACY_METERS_PER_SECOND = 0.2f;
    public static final float BEARING_ACCURACY_DEGREES = 2.0f;
    public static final double ALTITUDE_METERS = 0.0d;

    public final double latitude;
    public final double longitude;
    public final double altitude;
    public final float accuracyMeters;
    public final float bearingDegrees;
    public final float speedMetersPerSecond;
    public final long timeMillis;
    public final long elapsedRealtimeNanos;

    private LocationFields(double latitude, double longitude, double altitude, float accuracyMeters,
                           float bearingDegrees, float speedMetersPerSecond,
                           long timeMillis, long elapsedRealtimeNanos) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracyMeters = accuracyMeters;
        this.bearingDegrees = bearingDegrees;
        this.speedMetersPerSecond = speedMetersPerSecond;
        this.timeMillis = timeMillis;
        this.elapsedRealtimeNanos = elapsedRealtimeNanos;
    }

    public static LocationFields of(GeoPoint point, float speedMetersPerSecond, float bearingDegrees,
                                    float accuracyMeters, long timeMillis, long elapsedRealtimeNanos) {
        return new LocationFields(point.latitude, point.longitude, ALTITUDE_METERS, accuracyMeters,
                bearingDegrees, speedMetersPerSecond, timeMillis, elapsedRealtimeNanos);
    }

    /** API 26+ 才写入的垂直/速度/方位精度（值由字段自身决定，这里只表达「是否需要写」）。 */
    public float verticalAccuracyMeters() {
        return accuracyMeters;
    }
}
