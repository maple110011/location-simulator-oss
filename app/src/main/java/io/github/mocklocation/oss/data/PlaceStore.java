package io.github.mocklocation.oss.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import io.github.mocklocation.oss.core.GeoPoint;

/**
 * 收藏、历史与已存路线的读写入口。方法同步执行，调用方负责切到后台线程。
 */
public final class PlaceStore {
    public enum RecordType { HISTORY, FAVORITE }

    public static final class PlaceRecord {
        public final long id;
        public final String name;
        public final GeoPoint point;
        public final RecordType type;
        public final long timestamp;

        public PlaceRecord(long id, String name, GeoPoint point, RecordType type, long timestamp) {
            this.id = id;
            this.name = name;
            this.point = point;
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    public static final class SavedRoute {
        public final long id;
        public final String name;
        public final List<GeoPoint> points;
        public final double speedKmh;
        public final boolean loop;
        public final long updatedAt;

        public SavedRoute(long id, String name, List<GeoPoint> points, double speedKmh, boolean loop, long updatedAt) {
            this.id = id;
            this.name = name;
            this.points = points;
            this.speedKmh = speedKmh;
            this.loop = loop;
            this.updatedAt = updatedAt;
        }
    }

    private final PlaceDbHelper helper;

    public PlaceStore(Context context) {
        this.helper = new PlaceDbHelper(context.getApplicationContext());
    }

    // ------------------------------------------------------------ 记录

    /** 按坐标查重：原软件同样在插入收藏前按坐标查已存在记录。 */
    public PlaceRecord findPlace(GeoPoint point, RecordType type) {
        Cursor cursor = helper.getReadableDatabase().query(PlaceDbHelper.TABLE_RECORDS, null,
                PlaceDbHelper.COL_LATITUDE + "=? AND " + PlaceDbHelper.COL_LONGITUDE + "=? AND "
                        + PlaceDbHelper.COL_TYPE + "=?",
                new String[]{String.format(java.util.Locale.US, "%.6f", point.latitude),
                        String.format(java.util.Locale.US, "%.6f", point.longitude), type.name()},
                null, null, null);
        try {
            return cursor.moveToFirst() ? read(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public long insertPlace(String name, GeoPoint point, RecordType type) {
        ContentValues values = new ContentValues();
        values.put(PlaceDbHelper.COL_NAME, name);
        values.put(PlaceDbHelper.COL_LATITUDE, point.latitude);
        values.put(PlaceDbHelper.COL_LONGITUDE, point.longitude);
        values.put(PlaceDbHelper.COL_TYPE, type.name());
        values.put(PlaceDbHelper.COL_TIMESTAMP, System.currentTimeMillis());
        return helper.getWritableDatabase().insert(PlaceDbHelper.TABLE_RECORDS, null, values);
    }

    /** 历史只保留最近 N 条，防止无限增长。 */
    public void trimHistory(int keep) {
        helper.getWritableDatabase().execSQL("DELETE FROM " + PlaceDbHelper.TABLE_RECORDS
                + " WHERE " + PlaceDbHelper.COL_TYPE + "='HISTORY' AND " + PlaceDbHelper.COL_ID
                + " NOT IN (SELECT " + PlaceDbHelper.COL_ID + " FROM " + PlaceDbHelper.TABLE_RECORDS
                + " WHERE " + PlaceDbHelper.COL_TYPE + "='HISTORY' ORDER BY "
                + PlaceDbHelper.COL_TIMESTAMP + " DESC LIMIT " + Math.max(0, keep) + ")");
    }

    public void updatePlaceName(long id, String name) {
        ContentValues values = new ContentValues();
        values.put(PlaceDbHelper.COL_NAME, name);
        helper.getWritableDatabase().update(PlaceDbHelper.TABLE_RECORDS, values,
                PlaceDbHelper.COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void deletePlace(long id) {
        helper.getWritableDatabase().delete(PlaceDbHelper.TABLE_RECORDS,
                PlaceDbHelper.COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<PlaceRecord> listPlaces(RecordType type) {
        List<PlaceRecord> result = new ArrayList<>();
        Cursor cursor = helper.getReadableDatabase().query(PlaceDbHelper.TABLE_RECORDS, null,
                PlaceDbHelper.COL_TYPE + "=?", new String[]{type.name()},
                null, null, PlaceDbHelper.COL_TIMESTAMP + " DESC");
        try {
            while (cursor.moveToNext()) result.add(read(cursor));
        } finally {
            cursor.close();
        }
        return result;
    }

    public boolean isFavorite(GeoPoint point) {
        return findPlace(point, RecordType.FAVORITE) != null;
    }

    private PlaceRecord read(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_ID));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_NAME));
        double lat = cursor.getDouble(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_LATITUDE));
        double lon = cursor.getDouble(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_LONGITUDE));
        String type = cursor.getString(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_TYPE));
        long ts = cursor.getLong(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_TIMESTAMP));
        return new PlaceRecord(id, name, new GeoPoint(lat, lon), RecordType.valueOf(type), ts);
    }

    // ------------------------------------------------------------ 路线

    public long insertRoute(String name, List<GeoPoint> points, double speedKmh, boolean loop) {
        ContentValues values = new ContentValues();
        values.put(PlaceDbHelper.COL_NAME, name);
        values.put(PlaceDbHelper.COL_SPEED_KMH, speedKmh);
        values.put(PlaceDbHelper.COL_LOOP, loop ? 1 : 0);
        values.put(PlaceDbHelper.COL_POINTS, encodePoints(points));
        values.put(PlaceDbHelper.COL_UPDATED_AT, System.currentTimeMillis());
        return helper.getWritableDatabase().insert(PlaceDbHelper.TABLE_ROUTES, null, values);
    }

    public List<SavedRoute> listRoutes() {
        List<SavedRoute> result = new ArrayList<>();
        Cursor cursor = helper.getReadableDatabase().query(PlaceDbHelper.TABLE_ROUTES, null,
                null, null, null, null, PlaceDbHelper.COL_UPDATED_AT + " DESC");
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_NAME));
                double speed = cursor.getDouble(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_SPEED_KMH));
                boolean loop = cursor.getInt(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_LOOP)) != 0;
                String encoded = cursor.getString(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_POINTS));
                long updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(PlaceDbHelper.COL_UPDATED_AT));
                result.add(new SavedRoute(id, name, decodePoints(encoded), speed, loop, updatedAt));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public void deleteRoute(long id) {
        helper.getWritableDatabase().delete(PlaceDbHelper.TABLE_ROUTES,
                PlaceDbHelper.COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    static String encodePoints(List<GeoPoint> points) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(';');
            GeoPoint p = points.get(i);
            sb.append(String.format(java.util.Locale.US, "%.7f,%.7f", p.latitude, p.longitude));
        }
        return sb.toString();
    }

    static List<GeoPoint> decodePoints(String encoded) {
        List<GeoPoint> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return points;
        for (String pair : encoded.split(";")) {
            int comma = pair.indexOf(',');
            if (comma <= 0) continue;
            try {
                points.add(new GeoPoint(Double.parseDouble(pair.substring(0, comma)),
                        Double.parseDouble(pair.substring(comma + 1))));
            } catch (NumberFormatException ignored) { }
        }
        return points;
    }
}
