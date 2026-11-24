package io.github.mocklocation.oss.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 仅属于界面层的偏好：最近一次弹出「每日自检」的日期。
 *
 * <p>SettingsStore 属于数据层公共契约（不修改），因此界面专属的「今日是否已弹过自检」放在这里。</p>
 */
public final class UiPrefs {

    private static final String PREFS_NAME = "mock_location_ui_prefs";
    private static final String KEY_LAST_SELF_CHECK_DAY = "last_self_check_day";

    private final SharedPreferences prefs;

    public UiPrefs(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getLastSelfCheckDay() {
        return prefs.getString(KEY_LAST_SELF_CHECK_DAY, "");
    }

    public void setLastSelfCheckDay(String day) {
        prefs.edit().putString(KEY_LAST_SELF_CHECK_DAY, day).apply();
    }

    public static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }
}
