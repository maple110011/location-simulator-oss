package io.github.mocklocation.oss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.data.PlaceDbHelper;
import io.github.mocklocation.oss.data.PlaceStore;

/**
 * Robolectric 测试：本地记录库的 schema 与 CRUD。
 *
 * schema 断言直接对照约定 Room 建表：records(id,name,latitude,longitude,type,timestamp) 与
 * saved_routes(id,name,speedKmh,loop,points,updatedAt)。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PlaceStoreTest {

    private Context app;
    private PlaceStore store;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        store = new PlaceStore(app);
    }

    @Test
    public void schema与约定Room建表一致() {
        SQLiteDatabase db = new PlaceDbHelper(app).getReadableDatabase();
        List<String> tables = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        while (cursor.moveToNext()) tables.add(cursor.getString(0));
        cursor.close();
        assertTrue("应包含 records 表", tables.contains("records"));
        assertTrue("应包含 saved_routes 表", tables.contains("saved_routes"));

        assertEquals(Arrays.asList("id", "name", "latitude", "longitude", "type", "timestamp"),
                columns(db, "records"));
        assertEquals(Arrays.asList("id", "name", "speedKmh", "loop", "points", "updatedAt"),
                columns(db, "saved_routes"));
    }

    private List<String> columns(SQLiteDatabase db, String table) {
        List<String> result = new ArrayList<>();
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        while (cursor.moveToNext()) result.add(cursor.getString(1));
        cursor.close();
        return result;
    }

    @Test
    public void 收藏增删改查与查重() {
        GeoPoint point = new GeoPoint(39.9042, 116.4074);
        assertNull(store.findPlace(point, PlaceStore.RecordType.FAVORITE));

        long id = store.insertPlace("公司", point, PlaceStore.RecordType.FAVORITE);
        assertTrue(id > 0);
        assertNotNull(store.findPlace(point, PlaceStore.RecordType.FAVORITE));
        assertEquals(1, store.listPlaces(PlaceStore.RecordType.FAVORITE).size());

        store.updatePlaceName(id, "新公司");
        assertEquals("新公司", store.listPlaces(PlaceStore.RecordType.FAVORITE).get(0).name);

        assertTrue(store.isFavorite(point));
        store.deletePlace(id);
        assertNull(store.findPlace(point, PlaceStore.RecordType.FAVORITE));
        assertEquals(0, store.listPlaces(PlaceStore.RecordType.FAVORITE).size());
    }

    @Test
    public void 历史只保留最近N条() {
        for (int i = 0; i < 6; i++) {
            store.insertPlace("点" + i, new GeoPoint(39.9 + i * 0.001, 116.4), PlaceStore.RecordType.HISTORY);
        }
        assertEquals(6, store.listPlaces(PlaceStore.RecordType.HISTORY).size());
        store.trimHistory(2);
        assertEquals(2, store.listPlaces(PlaceStore.RecordType.HISTORY).size());
    }

    @Test
    public void 路线存取与坐标编码() {
        List<GeoPoint> points = Arrays.asList(
                new GeoPoint(39.9000, 116.4000),
                new GeoPoint(39.9009, 116.4000),
                new GeoPoint(39.9018, 116.4000));
        long id = store.insertRoute("晨跑", points, 12.5, true);
        assertTrue(id > 0);

        List<PlaceStore.SavedRoute> routes = store.listRoutes();
        assertEquals(1, routes.size());
        PlaceStore.SavedRoute route = routes.get(0);
        assertEquals("晨跑", route.name);
        assertEquals(12.5, route.speedKmh, 1e-6);
        assertTrue(route.loop);
        assertEquals(3, route.points.size());
        assertEquals(39.9009, route.points.get(1).latitude, 1e-6);
        assertEquals(116.4000, route.points.get(1).longitude, 1e-6);

        store.deleteRoute(id);
        assertEquals(0, store.listRoutes().size());
    }
}
