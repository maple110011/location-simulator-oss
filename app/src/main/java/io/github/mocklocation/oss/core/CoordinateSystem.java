package io.github.mocklocation.oss.core;

/** 支持的三种坐标系；设置里可切换，注入系统时统一换算为 WGS84。 */
public enum CoordinateSystem {
    WGS84("WGS84"),
    GCJ02("GCJ-02"),
    BD09("BD-09");

    public final String label;

    CoordinateSystem(String label) { this.label = label; }
}
