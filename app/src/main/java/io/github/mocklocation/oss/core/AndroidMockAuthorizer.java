package io.github.mocklocation.oss.core;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

/** 生产实现：用 AppOpsManager 检查 android:mock_location。 */
public final class AndroidMockAuthorizer implements MockAuthorizer {
    private final Context context;

    public AndroidMockAuthorizer(Context context) {
        this.context = context;
    }

    @Override
    public boolean isMockAppSelected() {
        return MockEngine.isSelectedMockApp(context);
    }
}
