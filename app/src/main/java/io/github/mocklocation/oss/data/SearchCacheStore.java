package io.github.mocklocation.oss.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索结果缓存，照抄原软件的 search_cache 方案：键为 "query_<小写查询>"，值为 JSON 数组文本，最多保留 5 条。
 */
public final class SearchCacheStore {
    public static final String PREFS_NAME = "search_cache";
    public static final int MAX_ENTRIES = 5;

    private final SharedPreferences prefs;

    public SearchCacheStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String get(String query) {
        return prefs.getString(key(query), null);
    }

    /** 写入一条缓存，超过上限时淘汰最早写入的键（SharedPreferences 不保存顺序，按值中的时间戳判断）。 */
    public void put(String query, String json) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key(query), json);
        Map<String, ?> all = prefs.getAll();
        List<String> keys = new ArrayList<>();
        for (String k : all.keySet()) {
            if (k.startsWith("query_") && !k.equals(key(query))) keys.add(k);
        }
        while (keys.size() >= MAX_ENTRIES) {
            editor.remove(keys.remove(0));
        }
        editor.apply();
    }

    public void clear() { prefs.edit().clear().apply(); }

    private static String key(String query) {
        return "query_" + (query == null ? "" : query.trim().toLowerCase(java.util.Locale.US));
    }
}
