package io.github.mocklocation.oss.core;

/** 复刻版支持的三种坐标系，与原软件的「Coordinate System」设置项一一对应。 */
public enum CoordinateSystem {
    WGS84("WGS84"),
    GCJ02("GCJ-02"),
    BD09("BD-09");

    public final String label;

    CoordinateSystem(String label) { this.label = label; }
}
