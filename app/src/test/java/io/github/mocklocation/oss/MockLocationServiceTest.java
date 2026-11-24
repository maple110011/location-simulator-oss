package io.github.mocklocation.oss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.mocklocation.oss.core.AndroidTimeSource;
import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.core.LocationFields;
import io.github.mocklocation.oss.core.MockEngine;
import io.github.mocklocation.oss.core.MockState;
import io.github.mocklocation.oss.core.ProviderSpec;
import io.github.mocklocation.oss.core.ProviderSink;
import io.github.mocklocation.oss.service.MockLocationService;

/**
 * Robolectric 测试：前台服务的 1 秒 tick 在真实 Android 框架桩上是否按设计运行。
 *
 * 注入端替换成记录型 ProviderSink（系统 mock 能力由 MockEngine 的纯 Java 测试覆盖），
 * 这里验证的是服务层：Intent 解析、主线程 Handler 调度、状态发布、通知、暂停/完成/停止。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MockLocationServiceTest {

    /** 记录所有注入调用的假 sink。 */
    private static final class RecordingSink implements ProviderSink {
        final List<ProviderSpec> specs = new ArrayList<>();
        final List<LocationFields> gpsFields = new ArrayList<>();
        final List<LocationFields> networkFields = new ArrayList<>();
        final List<String> disabled = new ArrayList<>();
        final List<String> removed = new ArrayList<>();

        @Override public boolean addTestProvider(ProviderSpec spec) { specs.add(spec); return true; }
        @Override public boolean setTestProviderEnabled(String name, boolean enabled) {
            if (!enabled) disabled.add(name);
            return true;
        }
        @Override public boolean removeTestProvider(String name) { removed.add(name); return true; }
        @Override public boolean setTestProviderStatus(String name, int status, long time) { return true; }
        @Override public boolean setTestProviderLocation(String name, LocationFields fields) {
            if ("gps".equals(name)) gpsFields.add(fields); else networkFields.add(fields);
            return true;
        }
    }

    /** 测试用服务：把注入引擎换成记录型实现。 */
    public static class TestService extends MockLocationService {
        static MockEngine testEngine;
        @Override protected MockEngine createMockEngine() {
            return testEngine;
        }
    }

    private RecordingSink sink;
    private ServiceController<TestService> controller;
    private TestService service;

    private Context app() {
        return ApplicationProvider.getApplicationContext();
    }

    @Before
    public void setUp() {
        AppOpsManager appOps = (AppOpsManager) app().getSystemService(Context.APP_OPS_SERVICE);
        Shadows.shadowOf(appOps).setMode(AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), app().getPackageName(), AppOpsManager.MODE_ALLOWED);

        sink = new RecordingSink();
        TestService.testEngine = MockEngine.forTesting(sink, () -> true, new AndroidTimeSource(), null);
        controller = Robolectric.buildService(TestService.class).create();
        service = controller.get();
        ShadowLooper.idleMainLooper();
    }

    private Intent staticIntent(double lat, double lng) {
        return new Intent(app(), MockLocationService.class)
                .setAction(MockLocationService.ACTION_START_MOCK)
                .putExtra(MockLocationService.EXTRA_LATITUDE, lat)
                .putExtra(MockLocationService.EXTRA_LONGITUDE, lng);
    }

    private Intent routeIntent(double[] lats, double[] lons, double speedMps, boolean loop) {
        return new Intent(app(), MockLocationService.class)
                .setAction(MockLocationService.ACTION_START_ROUTE)
                .putExtra(MockLocationService.EXTRA_ROUTE_LATITUDES, lats)
                .putExtra(MockLocationService.EXTRA_ROUTE_LONGITUDES, lons)
                .putExtra(MockLocationService.EXTRA_ROUTE_SPEED_MPS, speedMps)
                .putExtra(MockLocationService.EXTRA_ROUTE_LOOP, loop);
    }

    private static double[] line1000m() {
        double startLat = 39.9000;
        double endLat = startLat + 1000.0 / 111194.9;
        return new double[]{startLat, endLat};
    }

    @Test
    public void 单点模拟每秒注入同一坐标() {
        int rc = service.onStartCommand(staticIntent(39.9042, 116.4074), 0, 0);
        assertEquals(MockLocationService.START_REDELIVER_INTENT, rc);
        assertTrue(service.getState().running);
        assertTrue("启动时应建立 gps/network 测试 provider", sink.specs.size() >= 1);

        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        int afterFirst = sink.gpsFields.size();
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);
        assertTrue("1 秒 tick 应多次注入，实际 " + sink.gpsFields.size(), sink.gpsFields.size() >= afterFirst + 2);
        for (LocationFields f : sink.gpsFields) {
            assertEquals(39.9042, f.latitude, 1e-6);
            assertEquals(116.4074, f.longitude, 1e-6);
            assertEquals(3.0f, f.accuracyMeters, 1e-6);
            assertEquals(0.0f, f.speedMetersPerSecond, 1e-6);
        }
        for (LocationFields f : sink.networkFields) {
            assertEquals(10.0f, f.accuracyMeters, 1e-6);
        }
    }

    @Test
    public void 路线模拟推进并在完成后保持终点() {
        double[] lats = line1000m();
        double[] lons = {116.4000, 116.4000};
        service.onStartCommand(routeIntent(lats, lons, 100.0, false), 0, 0);
        assertTrue(service.getState().running);
        assertEquals(MockState.Mode.ROUTE, service.getState().mode);

        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);
        double traveled = service.getState().routeDistanceMeters;
        assertTrue("3 秒后应已推进，实际 " + traveled, traveled > 100.0);

        ShadowLooper.idleMainLooper(12, TimeUnit.SECONDS); // 100 m/s 跑 1000 米需要 10 秒
        MockState finished = service.getState();
        assertTrue("非循环路线应标记完成", finished.routeCompleted);
        assertTrue("走完后仍在运行（holding final location）", finished.running);
        LocationFields lastGps = sink.gpsFields.get(sink.gpsFields.size() - 1);
        assertEquals("走完后注入速度为 0", 0.0f, lastGps.speedMetersPerSecond, 1e-6);
    }

    @Test
    public void 暂停后不再推进恢复后继续() {
        double[] lats = line1000m();
        double[] lons = {116.4000, 116.4000};
        service.onStartCommand(routeIntent(lats, lons, 10.0, false), 0, 0);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        double before = service.getState().routeDistanceMeters;
        assertTrue(before > 0.0);

        service.onStartCommand(new Intent(app(), MockLocationService.class)
                .setAction(MockLocationService.ACTION_TOGGLE_ROUTE_PAUSE), 0, 0);
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);
        double pausedValue = service.getState().routeDistanceMeters;
        assertEquals("暂停期间不推进", before, pausedValue, 1e-6);
        assertTrue(service.getState().paused);

        service.onStartCommand(new Intent(app(), MockLocationService.class)
                .setAction(MockLocationService.ACTION_TOGGLE_ROUTE_PAUSE), 0, 0);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        assertTrue("恢复后继续推进且无跳变", service.getState().routeDistanceMeters > pausedValue);
        assertFalse(service.getState().paused);
    }

    @Test
    public void 停止后清理provider并结束运行() {
        service.onStartCommand(staticIntent(39.9042, 116.4074), 0, 0);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        service.onStartCommand(new Intent(app(), MockLocationService.class)
                .setAction(MockLocationService.ACTION_STOP_MOCK), 0, 0);
        ShadowLooper.idleMainLooper();
        assertFalse(service.getState().running);
        assertTrue(sink.disabled.contains("gps"));
        assertTrue(sink.disabled.contains("network"));
        assertTrue(sink.removed.contains("gps"));
        assertTrue(sink.removed.contains("network"));
        int countAfterStop = sink.gpsFields.size();
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);
        assertEquals("停止后不再注入", countAfterStop, sink.gpsFields.size());
    }

    @Test
    public void 未授权时不开始模拟() {
        AppOpsManager appOps = (AppOpsManager) app().getSystemService(Context.APP_OPS_SERVICE);
        Shadows.shadowOf(appOps).setMode(AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), app().getPackageName(), AppOpsManager.MODE_IGNORED);
        service.onStartCommand(staticIntent(39.9042, 116.4074), 0, 0);
        ShadowLooper.idleMainLooper();
        assertFalse("未授权时服务不应处于运行状态", service.getState().running);
        assertTrue("应给出失败原因", service.getState().error != null);
        assertNotNull(service.getState().error);
    }
}
