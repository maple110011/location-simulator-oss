package io.github.mocklocation.oss.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用设置：文件名 app_settings，键名以 pref_ 开头。
 */
public final class SettingsStore {
    public static final String PREFS_NAME = "app_settings";

    public static final String KEY_MAP_PROVIDER = "pref_map_provider";
    public static final String KEY_COORDINATE_SYSTEM = "pref_coordinate_system";
    public static final String KEY_SELF_CHECK_COMPLETED = "pref_self_check_completed";
    public static final String KEY_REVIEW_SUCCESS_COUNT = "pref_review_total_success";
    public static final String KEY_REVIEW_DISMISSED = "pref_review_button_dismissed";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getMapProvider() { return prefs.getString(KEY_MAP_PROVIDER, "auto"); }

    public void setMapProvider(String provider) { prefs.edit().putString(KEY_MAP_PROVIDER, provider).apply(); }

    public String getCoordinateSystem() { return prefs.getString(KEY_COORDINATE_SYSTEM, "WGS84"); }

    public void setCoordinateSystem(String system) { prefs.edit().putString(KEY_COORDINATE_SYSTEM, system).apply(); }

    public boolean isSelfCheckCompleted() { return prefs.getBoolean(KEY_SELF_CHECK_COMPLETED, false); }

    public void setSelfCheckCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_SELF_CHECK_COMPLETED, completed).apply();
    }

    public int getSuccessfulRuns() { return prefs.getInt(KEY_REVIEW_SUCCESS_COUNT, 0); }

    public int incrementSuccessfulRuns() {
        int next = getSuccessfulRuns() + 1;
        prefs.edit().putInt(KEY_REVIEW_SUCCESS_COUNT, next).apply();
        return next;
    }

    public boolean isReviewDismissed() { return prefs.getBoolean(KEY_REVIEW_DISMISSED, false); }

    public void setReviewDismissed(boolean dismissed) {
        prefs.edit().putBoolean(KEY_REVIEW_DISMISSED, dismissed).apply();
    }
}
