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

import java.util.concurrent.TimeUnit;

import io.github.mocklocation.oss.service.MockLocationService;

/**
 * 端到端（除真机外最接近的一层）：真实服务 + 生产 MockEngine + 记录型 LocationManager。
 *
 * 与 MockLocationServiceTest 的区别：那边替换了注入引擎来测服务逻辑；这里不替换任何东西，
 * 从 onStartCommand 一路走到 LocationManager 的系统接口，证明「服务启动 -> 授权判定 -> 建 provider ->
 * 每秒写入 -> 停止清理」整条链路在 Android 框架语义下是通的。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {RecordingLocationManagerShadow.class})
public class MockLocationServiceRealEngineTest {

    private Context app;
    private MockLocationService service;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        RecordingLocationManagerShadow.reset();
        AppOpsManager appOps = (AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
        Shadows.shadowOf(appOps).setMode(AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), app.getPackageName(), AppOpsManager.MODE_ALLOWED);
        ServiceController<MockLocationService> controller = Robolectric.buildService(MockLocationService.class).create();
        service = controller.get();
        ShadowLooper.idleMainLooper();
    }

    private Intent staticIntent(double lat, double lng) {
        return new Intent(app, MockLocationService.class)
                .setAction(MockLocationService.ACTION_START_MOCK)
                .putExtra(MockLocationService.EXTRA_LATITUDE, lat)
                .putExtra(MockLocationService.EXTRA_LONGITUDE, lng);
    }

    @Test
    public void 单点模拟从服务到系统接口全链路注入() {
        service.onStartCommand(staticIntent(22.5431, 114.0579), 0, 0);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);

        assertTrue(service.getState().running);
        assertEquals(2, RecordingLocationManagerShadow.addedProviders.size());
        assertFalse("第一秒之后应已有注入", RecordingLocationManagerShadow.gpsLocations.isEmpty());
        android.location.Location last = RecordingLocationManagerShadow.lastLocation("gps");
        assertEquals(22.5431, last.getLatitude(), 1e-9);
        assertEquals(114.0579, last.getLongitude(), 1e-9);
        assertEquals(3.0f, last.getAccuracy(), 1e-6);
        assertEquals(0.0f, last.getSpeed(), 1e-6);
    }

    private Intent routeIntent(double[] lats, double[] lons, double speedMps, boolean loop) {
        return new Intent(app, MockLocationService.class)
                .setAction(MockLocationService.ACTION_START_ROUTE)
                .putExtra(MockLocationService.EXTRA_ROUTE_LATITUDES, lats)
                .putExtra(MockLocationService.EXTRA_ROUTE_LONGITUDES, lons)
                .putExtra(MockLocationService.EXTRA_ROUTE_SPEED_MPS, speedMps)
                .putExtra(MockLocationService.EXTRA_ROUTE_LOOP, loop);
    }

    @Test
    public void 路线模拟全链路推进并在完成后停在终点() {
        double startLat = 39.9000;
        double endLat = startLat + 1000.0 / 111194.9;
        service.onStartCommand(routeIntent(new double[]{startLat, endLat}, new double[]{116.4, 116.4}, 100.0, false), 0, 0);
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);

        assertTrue(service.getState().running);
        assertEquals(io.github.mocklocation.oss.core.MockState.Mode.ROUTE, service.getState().mode);
        android.location.Location after3s = RecordingLocationManagerShadow.lastLocation("gps");
        assertNotNull(after3s);
        assertTrue("3 秒后应已北移", after3s.getLatitude() > startLat);
        assertEquals(116.4, after3s.getLongitude(), 1e-9);

        ShadowLooper.idleMainLooper(12, TimeUnit.SECONDS);
        assertTrue("非循环路线应完成", service.getState().routeCompleted);
        assertTrue("完成后仍在运行（保持终点）", service.getState().running);
        android.location.Location last = RecordingLocationManagerShadow.lastLocation("gps");
        assertEquals(endLat, last.getLatitude(), 1e-6);
        assertEquals(0.0f, last.getSpeed(), 1e-6);
    }

    @Test
    public void 暂停后系统接口收到的坐标不再变化() {
        double startLat = 39.9000;
        double endLat = startLat + 1000.0 / 111194.9;
        service.onStartCommand(routeIntent(new double[]{startLat, endLat}, new double[]{116.4, 116.4}, 10.0, false), 0, 0);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        int beforeCount = RecordingLocationManagerShadow.gpsLocations.size();
        android.location.Location pausedAt = RecordingLocationManagerShadow.lastLocation("gps");
        assertNotNull(pausedAt);

        service.onStartCommand(new Intent(app, MockLocationService.class)
                .setAction(MockLocationService.ACTION_TOGGLE_ROUTE_PAUSE), 0, 0);
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);
        assertTrue(service.getState().paused);
        android.location.Location afterPause = RecordingLocationManagerShadow.lastLocation("gps");
        assertTrue("暂停期间仍每秒注入", RecordingLocationManagerShadow.gpsLocations.size() > beforeCount);
        assertEquals("暂停期间坐标不应变化", pausedAt.getLatitude(), afterPause.getLatitude(), 1e-9);
        assertEquals(pausedAt.getLongitude(), afterPause.getLongitude(), 1e-9);
        assertEquals(0.0f, afterPause.getSpeed(), 1e-6);
    }

    @Test
    public void 停止模拟后清理系统provider() {
        service.onStartCommand(staticIntent(22.5431, 114.0579), 0, 0);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        service.onStartCommand(new Intent(app, MockLocationService.class)
                .setAction(MockLocationService.ACTION_STOP_MOCK), 0, 0);
        ShadowLooper.idleMainLooper();

        assertFalse(service.getState().running);
        assertTrue(RecordingLocationManagerShadow.enabledCalls.contains("gps=false"));
        assertTrue(RecordingLocationManagerShadow.removedProviders.contains("gps"));
        assertTrue(RecordingLocationManagerShadow.removedProviders.contains("network"));
        int count = RecordingLocationManagerShadow.gpsLocations.size();
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);
        assertEquals("停止后不应再注入", count, RecordingLocationManagerShadow.gpsLocations.size());
    }
}
