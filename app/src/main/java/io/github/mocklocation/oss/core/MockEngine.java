package io.github.mocklocation.oss.core;

import android.app.AppOpsManager;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * 模拟定位注入的 Android 适配层：AppOps「模拟位置应用」判定、LocationManager 适配、GMS Fused mock（反射）。
 *
 * 真正的编排逻辑在纯 Java 的 MockInjector 里（因此可被 JVM 自测覆盖）；本类只负责把 Android 能力接进去。
 */
public final class MockEngine {
    private static final String TAG = "MockEngine";

    public static final String PROVIDER_GPS = MockInjector.PROVIDER_GPS;
    public static final String PROVIDER_NETWORK = MockInjector.PROVIDER_NETWORK;
    public static final float ACCURACY_GPS_M = MockInjector.ACCURACY_GPS_M;
    public static final float ACCURACY_NETWORK_M = MockInjector.ACCURACY_NETWORK_M;

    /** Fused（GMS）mock 写入端的抽象；没有 GMS 的设备上实现为空。 */
    public interface FusedMockWriter {
        boolean write(GeoPoint point, LocationFields fields);

        void disable();
    }

    private final MockInjector injector;

    /** 生产构造器：自动装配 LocationManager、AppOps 与 GMS。 */
    public MockEngine(Context context) {
        this(context, new GmsFusedMockWriter(context.getApplicationContext()));
    }

    private MockEngine(Context context, FusedMockWriter fusedWriter) {
        Context app = context.getApplicationContext();
        LocationManager manager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        this.injector = new MockInjector(new AndroidLocationProviderSink(manager),
                new AndroidMockAuthorizer(app), new AndroidTimeSource(), fusedWriter);
    }

    /** 供测试构造（自定义 sink/authorizer/time/fused）。 */
    public static MockEngine forTesting(ProviderSink sink, MockAuthorizer authorizer, TimeSource time,
                                        FusedMockWriter fusedWriter) {
        return new MockEngine(new MockInjector(sink, authorizer, time, fusedWriter));
    }

    private MockEngine(MockInjector injector) {
        this.injector = injector;
    }

    /** 系统是否已把本应用选为「模拟位置应用」。对应原软件 MainActivity 的 Y() 判定。 */
    public static boolean isSelectedMockApp(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.getPackageName())
                    : appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            Log.w(TAG, "isSelectedMockApp: AppOps check failed", t);
            return false;
        }
    }

    public boolean ensureTestProviders() {
        return injector.ensureTestProviders();
    }

    public MockInjector.InjectionResult inject(GeoPoint point, float speedMps, float bearingDegrees) {
        return injector.inject(point, speedMps, bearingDegrees);
    }

    public void disableFusedMock() {
        injector.disableFusedMock();
    }

    public void cleanup() {
        injector.cleanup();
    }

    /** GMS Fused 定位 mock（反射调用公开 API），没有 GMS 时静默降级。 */
    private static final class GmsFusedMockWriter implements FusedMockWriter {
        private final Context context;
        private Object fusedClient;
        private boolean mockMode;

        GmsFusedMockWriter(Context context) {
            this.context = context;
        }

        @Override
        public boolean write(GeoPoint point, LocationFields fields) {
            try {
                if (fusedClient == null) {
                    Class<?> services = Class.forName("com.google.android.gms.location.LocationServices");
                    Method getClient = services.getMethod("getFusedLocationProviderClient", Context.class);
                    fusedClient = getClient.invoke(null, context);
                    if (fusedClient == null) return false;
                }
                if (!mockMode) {
                    fusedClient.getClass().getMethod("setMockMode", boolean.class).invoke(fusedClient, true);
                    mockMode = true;
                }
                Location location = new Location("fused");
                location.setLatitude(fields.latitude);
                location.setLongitude(fields.longitude);
                location.setAltitude(fields.altitude);
                location.setAccuracy(fields.accuracyMeters);
                location.setTime(fields.timeMillis);
                location.setElapsedRealtimeNanos(fields.elapsedRealtimeNanos);
                fusedClient.getClass().getMethod("setMockLocation", Location.class).invoke(fusedClient, location);
                return true;
            } catch (Throwable t) {
                if (mockMode) {
                    mockMode = false;
                    fusedClient = null;
                }
                Log.i(TAG, "fused mock unavailable: " + t.getClass().getSimpleName());
                return false;
            }
        }

        @Override
        public void disable() {
            if (fusedClient == null) return;
            try {
                fusedClient.getClass().getMethod("setMockMode", boolean.class).invoke(fusedClient, false);
            } catch (Throwable ignored) { }
            mockMode = false;
            fusedClient = null;
        }
    }
}
