package io.github.mocklocation.oss.ui;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.core.GeoPoint;

/** 界面上重复出现的格式化逻辑：坐标、距离、时间、默认名称。 */
public final class FormatUtil {

    private FormatUtil() {
    }

    public static String formatTimestamp(Context context, long millis) {
        String pattern = context.getString(R.string.time_pattern);
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }

    public static String formatCoordinate(Context context, GeoPoint point) {
        if (point == null) {
            return "";
        }
        return context.getString(R.string.search_result_coordinates, point.latitude, point.longitude);
    }

    public static String formatDistance(Context context, double meters) {
        if (meters >= 1000.0) {
            return context.getString(R.string.distance_kilometers, meters / 1000.0);
        }
        return context.getString(R.string.distance_meters, meters);
    }

    /** 地点默认名：名称为空时用「位置 #id」。 */
    public static String placeDisplayName(Context context, String name, long id) {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return context.getString(R.string.place_default_name, id);
    }

    /** 路线默认名：名称为空时用「路线 #id」。 */
    public static String routeDisplayName(Context context, String name, long id) {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return context.getString(R.string.route_default_name, id);
    }
}
