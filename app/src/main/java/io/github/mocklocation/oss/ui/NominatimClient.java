package io.github.mocklocation.oss.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.mocklocation.oss.data.SearchCacheStore;

/**
 * OpenStreetMap Nominatim 地址搜索客户端（HTTPS + 系统 HttpURLConnection，不引入额外依赖）。
 *
 * <p>本应用不使用私有地图 SDK，地址搜索改用 OpenStreetMap Nominatim。无网络、超时或返回非 200
 * 时走 {@link Callback#onFailure()}，由界面提示「搜索失败，请检查网络连接」。结果回调在主线程。</p>
 */
public final class NominatimClient {

    /** 搜索回调，全部在主线程调用。 */
    public interface Callback {
        void onResults(List<SearchResult> results);

        void onFailure();
    }

    private static final String ENDPOINT = "https://nominatim.openstreetmap.org/search";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int RESULT_LIMIT = 8;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final SearchCacheStore cache;

    public NominatimClient(Context context) {
        this.cache = new SearchCacheStore(context);
    }

    /**
     * 发起一次搜索。新的搜索会作废尚未返回的旧搜索，避免旧结果覆盖新结果。
     */
    public void search(final String query, final Callback callback) {
        final int requestId = generation.incrementAndGet();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<SearchResult> results;
                try {
                    results = fetchWithCache(query);
                } catch (Throwable t) {
                    results = null;
                }
                final List<SearchResult> fetched = results;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (requestId != generation.get()) {
                            return;
                        }
                        if (fetched == null) {
                            callback.onFailure();
                        } else {
                            callback.onResults(fetched);
                        }
                    }
                });
            }
        });
    }

    /** 先查 SearchCacheStore（SharedPreferences 缓存，最多 5 条），未命中再走网络并回写缓存。 */
    private List<SearchResult> fetchWithCache(String query) throws Exception {
        String cached = cache.get(query);
        if (cached != null) {
            try {
                return parse(cached);
            } catch (Exception ignored) {
                // 缓存损坏则回落到网络请求。
            }
        }
        String json = fetchJson(query);
        List<SearchResult> results = parse(json);
        try {
            cache.put(query, json);
        } catch (Throwable ignored) {
            // 缓存写入失败不影响本次搜索结果。
        }
        return results;
    }

    private String fetchJson(String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        String url = ENDPOINT + "?format=json&limit=" + RESULT_LIMIT + "&addressdetails=0&q=" + encoded;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "MockLocationOss/0.1 (open-source mock location clone)");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Nominatim HTTP " + code);
            }
            StringBuilder body = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            } finally {
                reader.close();
            }
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }

    private List<SearchResult> parse(String json) throws Exception {
        JSONArray array = new JSONArray(json);
        List<SearchResult> results = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.optString("display_name", "");
            double latitude = item.optDouble("lat", Double.NaN);
            double longitude = item.optDouble("lon", Double.NaN);
            if (name.isEmpty() || Double.isNaN(latitude) || Double.isNaN(longitude)) {
                continue;
            }
            results.add(new SearchResult(name, latitude, longitude));
        }
        return results;
    }

    public void shutdown() {
        generation.incrementAndGet();
        executor.shutdownNow();
    }
}
