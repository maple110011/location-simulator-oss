package io.github.mocklocation.oss.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 记录与路线的本地库。
 *
 * 表结构照抄原软件 Room 生成的建表语句（sources/D2/I.java:318-319）：
 * - records(id, name, latitude, longitude, type, timestamp)，type = HISTORY | FAVORITE；
 * - saved_routes(id, name, speedKmh, loop, points, updatedAt)，points 是 "lat,lng;lat,lng;..." 文本。
 * 这样迁移自原软件的数据（例如用相同 schema 导出的库）可以直接被复刻版读取。
 */
public final class PlaceDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "mock_location.db";
    public static final int DB_VERSION = 1;

    public static final String TABLE_RECORDS = "records";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_LATITUDE = "latitude";
    public static final String COL_LONGITUDE = "longitude";
    public static final String COL_TYPE = "type";
    public static final String COL_TIMESTAMP = "timestamp";

    public static final String TABLE_ROUTES = "saved_routes";
    public static final String COL_SPEED_KMH = "speedKmh";
    public static final String COL_LOOP = "loop";
    public static final String COL_POINTS = "points";
    public static final String COL_UPDATED_AT = "updatedAt";

    public PlaceDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_RECORDS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT, "
                + COL_LATITUDE + " REAL NOT NULL, "
                + COL_LONGITUDE + " REAL NOT NULL, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_TIMESTAMP + " INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_type ON "
                + TABLE_RECORDS + "(" + COL_TYPE + "," + COL_TIMESTAMP + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ROUTES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT, "
                + COL_SPEED_KMH + " REAL NOT NULL, "
                + COL_LOOP + " INTEGER NOT NULL, "
                + COL_POINTS + " TEXT NOT NULL, "
                + COL_UPDATED_AT + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 第一版没有历史迁移；后续版本在此按 oldVersion 逐级补列，不删库。
    }
}
