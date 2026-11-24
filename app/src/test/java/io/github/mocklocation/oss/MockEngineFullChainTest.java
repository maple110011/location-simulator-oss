package io.github.mocklocation.oss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.core.MockEngine;
import io.github.mocklocation.oss.core.MockInjector;

/**
 * 全链路测试（Robolectric）：生产实现 MockEngine -> 真实 LocationManager API -> 系统接口。
 *
 * 与纯 JVM 的 MockInjector 测试互补：这里不替换 sink，验证的是 AndroidLocationProviderSink
 * 确实把参数与坐标按正确形式交给了系统，以及 AppOps 授权判定真的会拦住注入。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {RecordingLocationManagerShadow.class})
public class MockEngineFullChainTest {

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        RecordingLocationManagerShadow.reset();
        setMockLocationOp(AppOpsManager.MODE_ALLOWED);
    }

    private void setMockLocationOp(int mode) {
        AppOpsManager appOps = (AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
        Shadows.shadowOf(appOps).setMode(AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), app.getPackageName(), mode);
    }

    @Test
    public void 生产引擎建立两个测试provider且参数正确() {
        MockEngine engine = new MockEngine(app);
        assertTrue(engine.ensureTestProviders());
        assertEquals(2, RecordingLocationManagerShadow.addedProviders.size());

        RecordingLocationManagerShadow.ProviderCall gps = find("gps");
        RecordingLocationManagerShadow.ProviderCall network = find("network");
        assertNotNull(gps);
        assertNotNull(network);
        assertFalse(gps.requiresNetwork);
        assertTrue(gps.requiresSatellite);
        assertFalse(gps.requiresCell);
        assertFalse(gps.hasMonetaryCost);
        assertTrue(gps.supportsAltitude && gps.supportsSpeed && gps.supportsBearing);
        assertEquals(1, gps.powerRequirement);
        assertEquals(1, gps.accuracy);

        assertTrue(network.requiresNetwork);
        assertFalse(network.requiresSatellite);
        assertFalse(network.requiresCell);
        assertTrue(network.supportsAltitude && network.supportsSpeed && network.supportsBearing);
        assertEquals(1, network.powerRequirement);

        assertTrue(RecordingLocationManagerShadow.enabledCalls.contains("gps=true"));
        assertTrue(RecordingLocationManagerShadow.enabledCalls.contains("network=true"));
    }

    @Test
    public void 生产引擎注入后系统接口收到正确坐标与精度() {
        MockEngine engine = new MockEngine(app);
        engine.ensureTestProviders();
        MockInjector.InjectionResult result = engine.inject(new GeoPoint(31.2304, 121.4737), 8.5f, 45f);
        assertTrue(result.success);

        assertEquals(1, RecordingLocationManagerShadow.gpsLocations.size());
        assertEquals(1, RecordingLocationManagerShadow.networkLocations.size());
        android.location.Location gps = RecordingLocationManagerShadow.lastLocation("gps");
        android.location.Location net = RecordingLocationManagerShadow.lastLocation("network");
        assertEquals(31.2304, gps.getLatitude(), 1e-9);
        assertEquals(121.4737, gps.getLongitude(), 1e-9);
        assertEquals(0.0, gps.getAltitude(), 1e-9);
        assertEquals(3.0f, gps.getAccuracy(), 1e-6);
        assertEquals(8.5f, gps.getSpeed(), 1e-6);
        assertEquals(45f, gps.getBearing(), 1e-6);
        assertTrue(gps.hasSpeed() && gps.hasBearing());
        assertEquals(10.0f, net.getAccuracy(), 1e-6);
        assertTrue(RecordingLocationManagerShadow.statusCalls.contains("gps=2"));
        assertTrue(RecordingLocationManagerShadow.statusCalls.contains("network=2"));
    }

    @Test
    public void AppOps拒绝时生产引擎不写入() {
        setMockLocationOp(AppOpsManager.MODE_ERRORED);
        MockEngine engine = new MockEngine(app);
        engine.ensureTestProviders();
        MockInjector.InjectionResult result = engine.inject(new GeoPoint(31.0, 121.0), 0f, 0f);
        assertFalse(result.success);
        assertTrue(RecordingLocationManagerShadow.gpsLocations.isEmpty());
    }

    @Test
    public void cleanup会禁用并移除provider() {
        MockEngine engine = new MockEngine(app);
        engine.ensureTestProviders();
        engine.cleanup();
        assertTrue(RecordingLocationManagerShadow.enabledCalls.contains("gps=false"));
        assertTrue(RecordingLocationManagerShadow.enabledCalls.contains("network=false"));
        assertTrue(RecordingLocationManagerShadow.removedProviders.contains("gps"));
        assertTrue(RecordingLocationManagerShadow.removedProviders.contains("network"));
    }

    private RecordingLocationManagerShadow.ProviderCall find(String name) {
        for (RecordingLocationManagerShadow.ProviderCall c : RecordingLocationManagerShadow.addedProviders) {
            if (name.equals(c.name)) return c;
        }
        return null;
    }
}
