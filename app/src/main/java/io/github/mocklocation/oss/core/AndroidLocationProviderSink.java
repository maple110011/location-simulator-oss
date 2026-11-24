package io.github.mocklocation.oss.core;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

/**
 * ProviderSink 的 Android 实现：把 ProviderSpec 落到 LocationManager.addTestProvider，
 * 把 LocationFields 落到 Location 对象后写入测试 provider。
 */
public final class AndroidLocationProviderSink implements ProviderSink {
    private static final String TAG = "ProviderSink";

    private final LocationManager locationManager;

    public AndroidLocationProviderSink(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    @Override
    public boolean addTestProvider(ProviderSpec spec) {
        if (locationManager == null) return false;
        try {
            try { locationManager.removeTestProvider(spec.name); } catch (Throwable ignored) { }
            locationManager.addTestProvider(spec.name, spec.requiresNetwork, spec.requiresSatellite,
                    spec.requiresCell, spec.hasMonetaryCost, spec.supportsAltitude, spec.supportsSpeed,
                    spec.supportsBearing, spec.powerRequirement, spec.accuracy);
            locationManager.setTestProviderEnabled(spec.name, true);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "addTestProvider(" + spec.name + ") failed", t);
            return false;
        }
    }

    @Override
    public boolean setTestProviderEnabled(String name, boolean enabled) {
        if (locationManager == null) return false;
        try { locationManager.setTestProviderEnabled(name, enabled); return true; }
        catch (Throwable t) { Log.w(TAG, "setTestProviderEnabled failed", t); return false; }
    }

    @Override
    public boolean removeTestProvider(String name) {
        if (locationManager == null) return false;
        try { locationManager.removeTestProvider(name); return true; }
        catch (Throwable t) { return false; }
    }

    @Override
    public boolean setTestProviderStatus(String name, int status, long timeMillis) {
        if (locationManager == null) return false;
        try { locationManager.setTestProviderStatus(name, status, null, timeMillis); return true; }
        catch (Throwable t) { Log.w(TAG, "setTestProviderStatus failed", t); return false; }
    }

    @Override
    public boolean setTestProviderLocation(String name, LocationFields fields) {
        if (locationManager == null) return false;
        try {
            Location location = new Location(name);
            location.setLatitude(fields.latitude);
            location.setLongitude(fields.longitude);
            location.setAltitude(fields.altitude);
            location.setAccuracy(fields.accuracyMeters);
            location.setTime(fields.timeMillis);
            location.setElapsedRealtimeNanos(fields.elapsedRealtimeNanos);
            location.setBearing(fields.bearingDegrees);
            location.setSpeed(fields.speedMetersPerSecond);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                location.setVerticalAccuracyMeters(fields.verticalAccuracyMeters());
                location.setSpeedAccuracyMetersPerSecond(LocationFields.SPEED_ACCURACY_METERS_PER_SECOND);
                location.setBearingAccuracyDegrees(LocationFields.BEARING_ACCURACY_DEGREES);
            }
            locationManager.setTestProviderLocation(name, location);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "setTestProviderLocation(" + name + ") failed", t);
            return false;
        }
    }
}
