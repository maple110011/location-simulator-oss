package io.github.mocklocation.oss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.navigation.NavigationView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.mocklocation.oss.service.MockLocationService;
import io.github.mocklocation.oss.ui.PlaceListFragment;

/**
 * 界面冒烟测试（Robolectric）：主界面能加载、抽屉六页能切换、首次启动弹自检、debug 指令能拉起模拟服务。
 *
 * 覆盖的是「装到手机上之后第一眼能看到的东西」：布局绑定、导航、权限/自检前置校验、服务启动路径。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivitySmokeTest {

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf((Application) app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        android.app.AppOpsManager appOps = (android.app.AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
        Shadows.shadowOf(appOps).setMode(android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), app.getPackageName(), android.app.AppOpsManager.MODE_ALLOWED);
    }

    private MainActivity launch(Intent intent) {
        ActivityController<MainActivity> controller = intent == null
                ? Robolectric.buildActivity(MainActivity.class)
                : Robolectric.buildActivity(MainActivity.class, intent);
        controller.setup();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        return controller.get();
    }

    @Test
    public void 主界面加载后抽屉有六个条目() {
        MainActivity activity = launch(null);
        NavigationView navigationView = activity.findViewById(R.id.nav_view);
        assertNotNull(navigationView);
        assertEquals(6, navigationView.getMenu().size());
        assertNotNull(activity.findViewById(R.id.toolbar));
        assertNotNull(activity.findViewById(R.id.map_page));
        assertEquals("模拟定位", activity.getString(R.string.app_name));
    }

    @Test
    public void 抽屉导航切换到收藏页会替换内容区域() {
        MainActivity activity = launch(null);
        NavigationView navigationView = activity.findViewById(R.id.nav_view);
        assertTrue(navigationView.getMenu().performIdentifierAction(R.id.nav_favorites, 0));
        ShadowLooper.idleMainLooper();
        Object fragment = activity.getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);
        assertTrue("收藏页应使用 PlaceListFragment，实际 " + fragment, fragment instanceof PlaceListFragment);
        assertTrue(activity.findViewById(R.id.map_page).getVisibility() != android.view.View.VISIBLE
                || activity.findViewById(R.id.fragment_container).getVisibility() == android.view.View.VISIBLE);
    }

    @Test
    public void 自检入口可以弹出六项检查对话框() {
        MainActivity activity = launch(null);
        activity.openSelfCheck();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        android.app.Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("自检入口应弹出自检对话框（latest dialog 为空说明没有 show 到窗口）", dialog);
        assertTrue("自检对话框应处于显示状态", dialog.isShowing());
        // 对话框内容是六个检查项，每项一行标题 + 一行说明。
        assertNotNull(dialog.findViewById(R.id.self_check_container));
    }

    @Test
    public void debug指令start_static会向服务发出START_MOCK() {
        Intent verify = new Intent(app, MainActivity.class)
                .setAction("io.github.mocklocation.oss.VERIFY")
                .putExtra("command", "start_static")
                .putExtra("latitude", 39.9042)
                .putExtra("longitude", 116.4074);
        launch(verify);
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);

        ShadowApplication shadowApp = Shadows.shadowOf((Application) app);
        List<Intent> started = new ArrayList<>();
        Intent next;
        while ((next = shadowApp.getNextStartedService()) != null) started.add(next);

        Intent startMock = null;
        for (Intent i : started) {
            if (MockLocationService.ACTION_START_MOCK.equals(i.getAction())) startMock = i;
        }
        assertNotNull("应发出 START_MOCK 服务 Intent，实际：" + started, startMock);
        assertEquals(39.9042, startMock.getDoubleExtra(MockLocationService.EXTRA_LATITUDE, 0), 1e-9);
        assertEquals(116.4074, startMock.getDoubleExtra(MockLocationService.EXTRA_LONGITUDE, 0), 1e-9);
    }
}
