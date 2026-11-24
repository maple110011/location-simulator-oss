package io.github.mocklocation.oss;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import io.github.mocklocation.oss.core.MockEngine;

/**
 * Robolectric 测试：生产环境的 AppOps「模拟位置应用」判定。
 *
 * 这条判定是开始模拟的硬性前置条件，等价于原软件 MainActivity 的 Y()。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MockEngineAndroidTest {

    private Context app() {
        return ApplicationProvider.getApplicationContext();
    }

    private void setMockLocationOp(int mode) {
        AppOpsManager appOps = (AppOpsManager) app().getSystemService(Context.APP_OPS_SERVICE);
        // OP_MOCK_LOCATION 是隐藏常量，不能用；AppOpsManager 只暴露字符串版 OPSTR_MOCK_LOCATION。
        Shadows.shadowOf(appOps).setMode(AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), app().getPackageName(), mode);
    }

    private String modeInfo() {
        AppOpsManager appOps = (AppOpsManager) app().getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        try {
            mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), app().getPackageName());
        } catch (Throwable t) {
            mode = -999;
        }
        return "appOps.mode=" + mode;
    }

    @Test
    public void 默认未授权时判定为未选中() {
        setMockLocationOp(AppOpsManager.MODE_DEFAULT);
        assertFalse(modeInfo(), MockEngine.isSelectedMockApp(app()));
    }

    @Test
    public void AppOps允许时判定为已选中() {
        setMockLocationOp(AppOpsManager.MODE_ALLOWED);
        assertTrue(modeInfo(), MockEngine.isSelectedMockApp(app()));
    }

    @Test
    public void AppOps显式拒绝时判定为未选中() {
        setMockLocationOp(AppOpsManager.MODE_ERRORED);
        assertFalse(modeInfo(), MockEngine.isSelectedMockApp(app()));
    }
}
