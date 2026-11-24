package io.github.mocklocation.oss;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用 LocationManager 影子：记录 addTestProvider / setTestProviderLocation 等调用。
 *
 * 生产代码（AndroidLocationProviderSink）走的是真实 LocationManager API；在 Robolectric 下
 * 用本影子替换系统实现后，就能在 JVM 上断言「生产调用确实带着正确的参数与坐标到达了系统接口」。
 */
@Implements(LocationManager.class)
public class RecordingLocationManagerShadow {

    /** 一次 addTestProvider 调用的完整参数。 */
    public static final class ProviderCall {
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

        ProviderCall(String name, boolean requiresNetwork, boolean requiresSatellite, boolean requiresCell,
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

    public static final List<ProviderCall> addedProviders = new ArrayList<>();
    public static final List<String> removedProviders = new ArrayList<>();
    public static final List<String> enabledCalls = new ArrayList<>();
    public static final List<String> statusCalls = new ArrayList<>();
    public static final List<Location> gpsLocations = new ArrayList<>();
    public static final List<Location> networkLocations = new ArrayList<>();
    public static boolean failAddProvider;

    public static void reset() {
        addedProviders.clear();
        removedProviders.clear();
        enabledCalls.clear();
        statusCalls.clear();
        gpsLocations.clear();
        networkLocations.clear();
        failAddProvider = false;
    }

    public static Location lastLocation(String provider) {
        List<Location> list = "gps".equals(provider) ? gpsLocations : networkLocations;
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    @Implementation
    protected void addTestProvider(String name, boolean requiresNetwork, boolean requiresSatellite,
                                   boolean requiresCell, boolean hasMonetaryCost, boolean supportsAltitude,
                                   boolean supportsSpeed, boolean supportsBearing, int powerRequirement, int accuracy) {
        if (failAddProvider) throw new IllegalArgumentException("addTestProvider blocked by test shadow");
        addedProviders.add(new ProviderCall(name, requiresNetwork, requiresSatellite, requiresCell,
                hasMonetaryCost, supportsAltitude, supportsSpeed, supportsBearing, powerRequirement, accuracy));
    }

    @Implementation
    protected void removeTestProvider(String name) {
        removedProviders.add(name);
    }

    @Implementation
    protected void setTestProviderEnabled(String name, boolean enabled) {
        enabledCalls.add(name + "=" + enabled);
    }

    @Implementation
    protected void setTestProviderStatus(String name, int status, Bundle extras, long timestamp) {
        statusCalls.add(name + "=" + status);
    }

    @Implementation
    protected void setTestProviderLocation(String name, Location location) {
        Location copy = new Location(location);
        if ("gps".equals(name)) gpsLocations.add(copy); else networkLocations.add(copy);
    }
}
