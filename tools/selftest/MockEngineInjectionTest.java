package io.github.mocklocation.oss.core;

import java.util.ArrayList;
import java.util.List;

/** JVM 自测（第三部分）：MockInjector 的注入参数与每秒采样，用记录型 ProviderSink 替代真实系统。 */
public final class MockEngineInjectionTest {
    private static int passed = 0;
    private static int failed = 0;

    static class RecordingSink implements ProviderSink {
        final List<ProviderSpec> specs = new ArrayList<>();
        final List<String> enabled = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
        final List<String> statuses = new ArrayList<>();
        final List<LocationFields> gpsFields = new ArrayList<>();
        final List<LocationFields> networkFields = new ArrayList<>();
        boolean failProviders;

        @Override public boolean addTestProvider(ProviderSpec spec) { specs.add(spec); return !failProviders; }
        @Override public boolean setTestProviderEnabled(String name, boolean on) { enabled.add(name + "=" + on); return true; }
        @Override public boolean removeTestProvider(String name) { removed.add(name); return true; }
        @Override public boolean setTestProviderStatus(String name, int status, long time) { statuses.add(name + "=" + status); return true; }
        @Override public boolean setTestProviderLocation(String name, LocationFields fields) {
            if ("gps".equals(name)) gpsFields.add(fields); else networkFields.add(fields);
            return true;
        }
    }

    static final class FakeTime implements TimeSource {
        long millis;
        long nanos;
        @Override public long currentTimeMillis() { return millis; }
        @Override public long elapsedRealtimeNanos() { return nanos; }
    }

    static final class FakeFused implements FusedMockWriter {
        int writes;
        int disables;
        boolean result = true;
        @Override public boolean write(GeoPoint point, LocationFields fields) { writes++; return result; }
        @Override public void disable() { disables++; }
    }

    public static void main(String[] args) {
        RecordingSink sink = new RecordingSink();
        FakeTime time = new FakeTime();
        time.millis = 1700000000000L;
        time.nanos = 987654321L;
        FakeFused fused = new FakeFused();
        MockInjector injector = new MockInjector(sink, () -> true, time, fused);

        // ---------- provider 参数与约定一致 ----------
        check("ensureTestProviders 成功", injector.ensureTestProviders());
        check("建立两个 provider", sink.specs.size() == 2);
        ProviderSpec gps = find(sink.specs, "gps");
        ProviderSpec network = find(sink.specs, "network");
        check("gps: requiresNetwork=false", gps != null && !gps.requiresNetwork);
        check("gps: requiresSatellite=true", gps != null && gps.requiresSatellite);
        check("gps: supportsAltitude/Speed/Bearing=true",
                gps != null && gps.supportsAltitude && gps.supportsSpeed && gps.supportsBearing);
        check("gps: powerRequirement=1, accuracy=1", gps != null && gps.powerRequirement == 1 && gps.accuracy == 1);
        check("network: requiresNetwork=true", network != null && network.requiresNetwork);
        check("network: requiresSatellite=false", network != null && !network.requiresSatellite);
        check("network: requiresCell=false, hasMonetaryCost=false", network != null && !network.requiresCell && !network.hasMonetaryCost);

        // ---------- 授权失败时不写入 ----------
        MockInjector denied = new MockInjector(sink, () -> false, time, fused);
        MockInjector.InjectionResult deniedResult = denied.inject(new GeoPoint(39.9, 116.4), 0f, 0f);
        check("未授权时注入失败", !deniedResult.success && deniedResult.error != null);
        check("未授权时不写 provider", sink.gpsFields.isEmpty());

        // ---------- 正常注入：两条通道的字段 ----------
        GeoPoint point = new GeoPoint(39.9042, 116.4074);
        MockInjector.InjectionResult result = injector.inject(point, 12.5f, 90f);
        check("注入成功", result.success && result.gps && result.network);
        check("gps 与 network 各写一次", sink.gpsFields.size() == 1 && sink.networkFields.size() == 1);
        LocationFields gpsF = sink.gpsFields.get(0);
        LocationFields netF = sink.networkFields.get(0);
        check("gps 精度 3 米", gpsF.accuracyMeters == 3.0f);
        check("network 精度 10 米", netF.accuracyMeters == 10.0f);
        check("两通道坐标相同且等于下发坐标",
                gpsF.latitude == point.latitude && netF.latitude == point.latitude
                        && gpsF.longitude == point.longitude && netF.longitude == point.longitude);
        check("高度为 0", gpsF.altitude == 0.0 && netF.altitude == 0.0);
        check("速度/方位透传", gpsF.speedMetersPerSecond == 12.5f && gpsF.bearingDegrees == 90f);
        check("time/elapsedRealtimeNanos 来自 TimeSource",
                gpsF.timeMillis == time.millis && gpsF.elapsedRealtimeNanos == time.nanos);
        check("状态先置 AVAILABLE", sink.statuses.size() == 2 && sink.statuses.get(0).equals("gps=2"));
        check("Fused 写入一次", fused.writes == 1);

        // ---------- Fused 失败不影响主通道 ----------
        FakeFused badFused = new FakeFused();
        badFused.result = false;
        MockInjector injectorBadFused = new MockInjector(sink, () -> true, time, badFused);
        MockInjector.InjectionResult r2 = injectorBadFused.inject(point, 0f, 0f);
        check("Fused 失败时仍算注入成功", r2.success && !r2.fused);

        // ---------- 两个 provider 都失败才算失败 ----------
        MockInjector injectorAllFail = new MockInjector(new RecordingSink() {
            @Override public boolean setTestProviderLocation(String name, LocationFields fields) { return false; }
        }, () -> true, time, null);
        MockInjector.InjectionResult r3 = injectorAllFail.inject(point, 0f, 0f);
        check("两个 provider 都失败时返回失败", !r3.success && r3.error != null);

        // ---------- 清理 ----------
        injector.cleanup();
        check("cleanup 禁用两个 provider", sink.enabled.contains("gps=false") && sink.enabled.contains("network=false"));
        check("cleanup 移除两个 provider", sink.removed.contains("gps") && sink.removed.contains("network"));
        check("cleanup 关闭 Fused mock 模式", fused.disables == 1);

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) System.exit(1);
    }

    private static ProviderSpec find(List<ProviderSpec> specs, String name) {
        for (ProviderSpec s : specs) if (s.name.equals(name)) return s;
        return null;
    }

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("[PASS] " + name); }
        else { failed++; System.out.println("[FAIL] " + name); }
    }
}
