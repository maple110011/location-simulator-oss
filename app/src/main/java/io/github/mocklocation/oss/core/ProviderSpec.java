package io.github.mocklocation.oss.core;

/** addTestProvider 的参数集合（纯 Java 数据对象，便于在 JVM 上断言注入参数）。 */
public final class ProviderSpec {
    public final String name;
    public final boolean requiresNetwork;
    public final boolean requiresSatellite;
    public final boolean requiresCell;
    public final boolean hasMonetaryCost;
    public final boolean supportsAltitude;
    public final boolean supportsSpeed;
    public final boolean supportsBearing;
    public final int powerRequirement;
    public final int accuracy;

    public ProviderSpec(String name, boolean requiresNetwork, boolean requiresSatellite, boolean requiresCell,
                        boolean hasMonetaryCost, boolean supportsAltitude, boolean supportsSpeed,
                        boolean supportsBearing, int powerRequirement, int accuracy) {
        this.name = name;
        this.requiresNetwork = requiresNetwork;
        this.requiresSatellite = requiresSatellite;
        this.requiresCell = requiresCell;
        this.hasMonetaryCost = hasMonetaryCost;
        this.supportsAltitude = supportsAltitude;
        this.supportsSpeed = supportsSpeed;
        this.supportsBearing = supportsBearing;
        this.powerRequirement = powerRequirement;
        this.accuracy = accuracy;
    }
}
