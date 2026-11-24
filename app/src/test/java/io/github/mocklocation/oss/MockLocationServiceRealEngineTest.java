package io.github.mocklocation.oss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
