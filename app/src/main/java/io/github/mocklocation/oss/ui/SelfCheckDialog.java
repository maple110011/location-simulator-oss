package io.github.mocklocation.oss.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.core.MockEngine;
import io.github.mocklocation.oss.data.SettingsStore;

/**
 * 「模拟定位自检」对话框：六项系统设置检查，未通过的项给出跳转按钮。
 *
 * <p>六项检查：开发者选项、模拟位置应用（AppOps）、定位权限、通知权限、系统定位开关、电池优化。
 * 判断方式：开发者选项读 Settings.Global.DEVELOPMENT_SETTINGS_ENABLED；模拟位置应用用
 * MockEngine.isSelectedMockApp；其余用 Android 标准 API。</p>
 */
public final class SelfCheckDialog {

    /** 一项自检结果。 */
    private static final class Check {
        final int titleRes;
        final int detailRes;
        final boolean passed;
        final Intent intent;

        Check(int titleRes, int detailRes, boolean passed, Intent intent) {
            this.titleRes = titleRes;
            this.detailRes = detailRes;
            this.passed = passed;
            this.intent = intent;
        }
    }

    private SelfCheckDialog() {
    }

    public static void show(final Activity activity, final SettingsStore settingsStore,
                            final Runnable onCompleted) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_self_check, null);
        final LinearLayout container = content.findViewById(R.id.self_check_container);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.self_check_title)
                .setView(content)
                .setPositiveButton(R.string.action_done, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        settingsStore.setSelfCheckCompleted(true);
                        if (onCompleted != null) {
                            onCompleted.run();
                        }
                    }
                })
                .setNegativeButton(R.string.action_recheck, null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        populate(activity, container);
                    }
                });
            }
        });
        populate(activity, container);
        dialog.show();
    }

    private static void populate(Activity activity, LinearLayout container) {
        container.removeAllViews();
        List<Check> checks = new ArrayList<>();
        checks.add(new Check(R.string.self_check_developer_options,
                R.string.self_check_developer_options_detail,
                isDeveloperOptionsEnabled(activity), developmentSettingsIntent()));
        checks.add(new Check(R.string.self_check_mock_app,
                R.string.self_check_mock_app_detail,
                isMockAppSelected(activity), developmentSettingsIntent()));
        checks.add(new Check(R.string.self_check_location_permission,
                R.string.self_check_location_permission_detail,
                hasLocationPermission(activity), appDetailsIntent(activity)));
        checks.add(new Check(R.string.self_check_notification_permission,
                R.string.self_check_notification_permission_detail,
                areNotificationsEnabled(activity), notificationSettingsIntent(activity)));
        checks.add(new Check(R.string.self_check_location_switch,
                R.string.self_check_location_switch_detail,
                isLocationSwitchOn(activity),
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        checks.add(new Check(R.string.self_check_battery_optimization,
                R.string.self_check_battery_optimization_detail,
                isIgnoringBatteryOptimizations(activity),
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        for (Check check : checks) {
            addRow(activity, container, check);
        }
    }

    private static void addRow(final Activity activity, LinearLayout container, final Check check) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_self_check, container, false);
        ImageView icon = row.findViewById(R.id.iv_check_icon);
        TextView title = row.findViewById(R.id.tv_check_title);
        TextView detail = row.findViewById(R.id.tv_check_detail);
        View action = row.findViewById(R.id.btn_check_action);
        icon.setImageResource(check.passed ? R.drawable.ic_check : R.drawable.ic_warning);
        title.setText(check.titleRes);
        if (check.passed) {
            detail.setText(R.string.self_check_pass);
            action.setVisibility(View.GONE);
        } else {
            detail.setText(check.detailRes);
            action.setVisibility(View.VISIBLE);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openIntent(activity, check.intent);
                }
            });
        }
        container.addView(row);
    }

    private static void openIntent(Activity activity, Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.self_check_jump_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ 六项判定

    private static boolean isDeveloperOptionsEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isMockAppSelected(Context context) {
        try {
            return MockEngine.isSelectedMockApp(context);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean areNotificationsEnabled(Context context) {
        try {
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null && manager.areNotificationsEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isLocationSwitchOn(Context context) {
        try {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return manager != null && (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isIgnoringBatteryOptimizations(Context context) {
        try {
            PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return manager != null && manager.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ 跳转 Intent

    private static Intent developmentSettingsIntent() {
        return new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
    }

    private static Intent appDetailsIntent(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
    }

    private static Intent notificationSettingsIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            return intent;
        }
        return appDetailsIntent(context);
    }
}
