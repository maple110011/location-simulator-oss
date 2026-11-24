package io.github.mocklocation.oss.core;

/**
 * 模拟定位注入的编排层（纯 Java，可在 JVM 上验证）：负责 provider 的建立/清理与每秒坐标写入。
 *
 * Android 侧只保留 MockEngine（AppOps 判定 + LocationManager/GMS 适配），编排逻辑放在这里，
 * 因此「addTestProvider 参数、gps 3 米 / network 10 米精度、每秒字段」都能被自动化测试覆盖。
 */
public final class MockInjector {
    public static final String PROVIDER_GPS = "gps";
    public static final String PROVIDER_NETWORK = "network";

    /** 注入精度约定：gps 3 米、network 10 米。 */
    public static final float ACCURACY_GPS_M = LocationFields.ACCURACY_GPS_M;
    public static final float ACCURACY_NETWORK_M = LocationFields.ACCURACY_NETWORK_M;

    private static final int POWER_REQUIREMENT = 1;   // POWER_LOW
    private static final int ACCURACY = 1;            // ACCURACY_FINE
    private static final int STATUS_AVAILABLE = 2;    // LocationProvider.AVAILABLE

    private final ProviderSink sink;
    private final MockAuthorizer authorizer;
    private final TimeSource time;
    private final FusedMockWriter fusedWriter;

    public MockInjector(ProviderSink sink, MockAuthorizer authorizer, TimeSource time,
                        FusedMockWriter fusedWriter) {
        this.sink = sink;
        this.authorizer = authorizer;
        this.time = time;
        this.fusedWriter = fusedWriter;
    }

    public boolean ensureTestProviders() {
        boolean gps = sink.addTestProvider(gpsSpec());
        boolean network = sink.addTestProvider(networkSpec());
        return gps || network;
    }

    public static ProviderSpec gpsSpec() {
        return new ProviderSpec(PROVIDER_GPS, false, true,
                false, false, true, true, true, POWER_REQUIREMENT, ACCURACY);
    }

    public static ProviderSpec networkSpec() {
        return new ProviderSpec(PROVIDER_NETWORK, true, false,
                false, false, true, true, true, POWER_REQUIREMENT, ACCURACY);
    }

    public InjectionResult inject(GeoPoint point, float speedMps, float bearingDegrees) {
        if (point == null) return InjectionResult.failure("位置为空");
        if (!authorizer.isMockAppSelected()) {
            return InjectionResult.failure("未把本应用设为模拟位置应用");
        }
        long nowMillis = time.currentTimeMillis();
        long elapsedNanos = time.elapsedRealtimeNanos();
        boolean gpsOk = writeProvider(PROVIDER_GPS, ACCURACY_GPS_M, point, speedMps, bearingDegrees, nowMillis, elapsedNanos);
        boolean networkOk = writeProvider(PROVIDER_NETWORK, ACCURACY_NETWORK_M, point, speedMps, bearingDegrees, nowMillis, elapsedNanos);
        boolean fusedOk = false;
        if (fusedWriter != null) {
            fusedOk = fusedWriter.write(point,
                    LocationFields.of(point, speedMps, bearingDegrees, ACCURACY_GPS_M, nowMillis, elapsedNanos));
        }
        if (!gpsOk && !networkOk) {
            return InjectionResult.failure("gps 与 network 两个 provider 都写入失败");
        }
        return InjectionResult.success(gpsOk, networkOk, fusedOk);
    }

    private boolean writeProvider(String provider, float accuracy, GeoPoint point, float speedMps,
                                  float bearingDegrees, long nowMillis, long elapsedNanos) {
        LocationFields fields = LocationFields.of(point, speedMps, bearingDegrees, accuracy, nowMillis, elapsedNanos);
        sink.setTestProviderStatus(provider, STATUS_AVAILABLE, nowMillis);
        return sink.setTestProviderLocation(provider, fields);
    }

    public void disableFusedMock() {
        if (fusedWriter != null) fusedWriter.disable();
    }

    public void cleanup() {
        disableFusedMock();
        for (String provider : new String[]{PROVIDER_GPS, PROVIDER_NETWORK}) {
            sink.setTestProviderEnabled(provider, false);
            sink.removeTestProvider(provider);
        }
    }

    /** 一次注入的结果，用于把失败原因呈现到通知与界面。 */
    public static final class InjectionResult {
        public final boolean success;
        public final boolean gps;
        public final boolean network;
        public final boolean fused;
        public final String error;

        private InjectionResult(boolean success, boolean gps, boolean network, boolean fused, String error) {
            this.success = success;
            this.gps = gps;
            this.network = network;
            this.fused = fused;
            this.error = error;
        }

        static InjectionResult success(boolean gps, boolean network, boolean fused) {
            return new InjectionResult(true, gps, network, fused, null);
        }

        static InjectionResult failure(String error) {
            return new InjectionResult(false, false, false, false, error);
        }
    }
}
