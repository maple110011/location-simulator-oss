package io.github.mocklocation.oss.core;

/** 一个 WGS84 经纬度点。刻意不用 android.location.Location，便于在 JVM 上做单元测试。 */
public final class GeoPoint {
    public final double latitude;
    public final double longitude;

    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean isValid() {
        return latitude >= -90.0 && latitude <= 90.0 && longitude >= -180.0 && longitude <= 180.0;
    }

    @Override public String toString() {
        return String.format(java.util.Locale.US, "%.6f,%.6f", latitude, longitude);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof GeoPoint)) return false;
        GeoPoint p = (GeoPoint) o;
        return Double.compare(latitude, p.latitude) == 0 && Double.compare(longitude, p.longitude) == 0;
    }

    @Override public int hashCode() {
        return Double.hashCode(latitude) * 31 + Double.hashCode(longitude);
    }
}
