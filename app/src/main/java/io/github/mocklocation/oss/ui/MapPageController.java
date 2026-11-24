package io.github.mocklocation.oss.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.core.GeoMath;
import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.core.MockState;

/**
 * 地图页控制器：承载 map.html 的 WebView、顶部搜索、底部控制面板与路线参数。
 *
 * <p>所有会改变全局状态的按钮都通过 {@link UiHost} 回到 MainActivity；本类只负责视图与 WebView 桥。
 * JS 接口约定见 staging/assets/map.html：Android.onMapReady() / Android.onMapTap(lat,lng) 由页面调用，
 * 本类调用 setTarget/setHere/setRoute/setMode/setTileLayer/centerOn/fitRoute/clearXxx。</p>
 */
public final class MapPageController {

    private final UiHost host;
    private final Context context;

    private final WebView webView;
    private final EditText searchInput;
    private final ImageButton searchButton;
    private final ImageButton clearSearchButton;
    private final MaterialCardView searchResultsCard;
    private final RecyclerView searchResultsList;
    private final LinearLayoutManager searchLayoutManager;

    private final MaterialCardView bottomPanel;
    private final TextView selectedCoordinates;
    private final TextView mockStatus;
    private final EditText locationName;
    private final MaterialButtonToggleGroup modeToggle;
    private final MaterialButton startMockButton;
    private final MaterialButton stopMockButton;
    private final FloatingActionButton myLocationButton;

    private final View routeSection;
    private final TextView routeStatus;
    private final SeekBar routeSpeedSeekBar;
    private final EditText routeSpeedInput;
    private final TextView routeSpeedValue;
    private final SwitchMaterial routeLoopSwitch;
    private final MaterialButton startRouteButton;
    private final MaterialButton pauseRouteButton;
    private final MaterialButton clearRouteButton;
    private final MaterialButton saveRouteButton;

    private final SearchResultAdapter searchAdapter;
    private final NominatimClient nominatim;

    private final List<String> pendingScripts = new ArrayList<>();
    private boolean mapReady;
    private boolean destroyed;
    private boolean suppressModeCallback;
    private boolean updatingSpeed;
    private boolean updatingLoop;

    public MapPageController(UiHost host, @NonNull View root) {
        this.host = host;
        this.context = root.getContext();
        this.nominatim = new NominatimClient(context);

        webView = root.findViewById(R.id.map_web_view);
        searchInput = root.findViewById(R.id.et_search);
        searchButton = root.findViewById(R.id.btn_search);
        clearSearchButton = root.findViewById(R.id.btn_clear_search);
        searchResultsCard = root.findViewById(R.id.search_results_card);
        searchResultsList = root.findViewById(R.id.search_results_list);
        bottomPanel = root.findViewById(R.id.bottom_panel);
        selectedCoordinates = root.findViewById(R.id.tv_selected_coordinates);
        mockStatus = root.findViewById(R.id.tv_mock_status);
        locationName = root.findViewById(R.id.et_location_name);
        modeToggle = root.findViewById(R.id.mode_toggle);
        startMockButton = root.findViewById(R.id.btn_start_mock);
        stopMockButton = root.findViewById(R.id.btn_stop_mock);
        myLocationButton = root.findViewById(R.id.btn_my_location);
        routeSection = root.findViewById(R.id.route_section);
        routeStatus = root.findViewById(R.id.tv_route_status);
        routeSpeedSeekBar = root.findViewById(R.id.sb_route_speed);
        routeSpeedInput = root.findViewById(R.id.et_route_speed);
        routeSpeedValue = root.findViewById(R.id.tv_speed_value);
        routeLoopSwitch = root.findViewById(R.id.sw_route_loop);
        startRouteButton = root.findViewById(R.id.btn_start_route);
        pauseRouteButton = root.findViewById(R.id.btn_pause_route);
        clearRouteButton = root.findViewById(R.id.btn_clear_route);
        saveRouteButton = root.findViewById(R.id.btn_save_route);

        searchLayoutManager = new LinearLayoutManager(context);
        searchResultsList.setLayoutManager(searchLayoutManager);
        searchAdapter = new SearchResultAdapter(new SearchResultAdapter.Listener() {
            @Override
            public void onSearchResultClick(SearchResult result) {
                hideSearchResults();
                hideKeyboard();
                host.onAddressSelected(result.displayName, result.latitude, result.longitude);
            }
        });
        searchResultsList.setAdapter(searchAdapter);

        setupWebView();
        setupSearch();
        setupModeToggle();
        setupRouteControls();
        setupButtons();
    }

    // ------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setBackgroundColor(ContextCompat.getColor(context, R.color.color_background));
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new MapJsBridge(), "Android");
        webView.loadUrl("file:///android_asset/map.html");
    }

    /** map.html 暴露给 Android 的两个回调；都在 WebView 的 JavaBridge 线程触发，转发到主线程。 */
    private final class MapJsBridge {
        @JavascriptInterface
        public void onMapReady() {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    onMapReadyInternal();
                }
            });
        }

        @JavascriptInterface
        public void onMapTap(final double latitude, final double longitude) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    host.onMapTap(latitude, longitude);
                }
            });
        }
    }

    private void onMapReadyInternal() {
        if (destroyed) {
            return;
        }
        mapReady = true;
        for (String script : pendingScripts) {
            webView.evaluateJavascript(script, null);
        }
        pendingScripts.clear();
        applyModeInternal(host.getMode());
        applyTileLayer(host.getMapProvider());
        if (host.getSelectedPoint() != null) {
            showSelected(host.getSelectedPoint(), host.getSelectedLabel());
        }
        renderRoute(host.getRoutePoints(), host.getRouteSpeedKmh(), host.isRouteLoop());
    }

    private void runJs(String script) {
        if (destroyed || webView == null) {
            return;
        }
        if (!mapReady) {
            pendingScripts.add(script);
            return;
        }
        webView.evaluateJavascript(script, null);
    }

    /** 把对象转成 JS 单引号字符串字面量，避免用户输入破坏脚本。 */
    private static String jsQuote(String text) {
        if (text == null) {
            return "''";
        }
        StringBuilder builder = new StringBuilder("'");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '\\') {
                builder.append('\\');
            }
            if (c != '\n' && c != '\r') {
                builder.append(c);
            }
        }
        return builder.append('\'').toString();
    }

    // ------------------------------------------------------------ 地图绘制

    public void applyTileLayer(String provider) {
        String layer = "osm";
        if ("carto".equals(provider)) {
            layer = "carto";
        } else if ("opentopo".equals(provider)) {
            layer = "opentopo";
        }
        runJs("setTileLayer(" + jsQuote(layer) + ");");
    }

    public void applyMode(MockState.Mode mode) {
        applyModeInternal(mode);
    }

    private void applyModeInternal(MockState.Mode mode) {
        boolean routeMode = mode == MockState.Mode.ROUTE;
        suppressModeCallback = true;
        modeToggle.check(routeMode ? R.id.btn_route_mode : R.id.btn_location_mode);
        suppressModeCallback = false;
        routeSection.setVisibility(routeMode ? View.VISIBLE : View.GONE);
        runJs("setMode(" + jsQuote(routeMode ? "route" : "location") + ");");
    }

    public void showSelected(GeoPoint point, String label) {
        if (point == null) {
            selectedCoordinates.setText(R.string.selected_location_none);
            locationName.setText("");
            runJs("clearTarget();");
            return;
        }
        selectedCoordinates.setText(FormatUtil.formatCoordinate(context, point));
        if (label != null) {
            locationName.setText(label);
        }
        runJs("setTarget(" + point.latitude + "," + point.longitude + "," + jsQuote(label) + ");");
    }

    public void showHere(GeoPoint point) {
        if (point == null) {
            runJs("clearHere();");
            return;
        }
        runJs("setHere(" + point.latitude + "," + point.longitude + ");");
    }

    public void centerOn(GeoPoint point, int zoom) {
        if (point == null) {
            return;
        }
        runJs("centerOn(" + point.latitude + "," + point.longitude + "," + zoom + ");");
    }

    public void fitRoute() {
        runJs("fitRoute();");
    }

    /** 把路线点渲染到地图并刷新面板里的点数与总长。 */
    public void renderRoute(List<GeoPoint> points, double speedKmh, boolean loop) {
        setSpeedUi(speedKmh);
        setLoopUi(loop);
        if (points == null || points.isEmpty()) {
            runJs("clearRoute();");
            routeStatus.setText(R.string.route_stats_empty);
            return;
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            GeoPoint point = points.get(i);
            json.append("{\"lat\":").append(point.latitude).append(",\"lng\":").append(point.longitude).append('}');
        }
        json.append(']');
        runJs("setRoute(" + json + ");");
        double totalMeters = 0.0;
        for (int i = 1; i < points.size(); i++) {
            totalMeters += GeoMath.distanceMeters(points.get(i - 1), points.get(i));
        }
        routeStatus.setText(context.getString(R.string.route_stats, points.size(),
                FormatUtil.formatDistance(context, totalMeters)));
    }

    public void clearRouteDisplay() {
        runJs("clearRoute();");
        routeStatus.setText(R.string.route_stats_empty);
    }

    public String getLocationName() {
        return locationName.getText().toString().trim();
    }

    // ------------------------------------------------------------ 状态刷新

    /** 根据服务状态刷新按钮可见性、状态胶囊与坐标显示。 */
    public void updateState(MockState state) {
        boolean running = state != null && state.running;
        MockState.Mode currentMode = running ? state.mode : host.getMode();
        boolean routeMode = currentMode == MockState.Mode.ROUTE;
        boolean completed = running && state.routeCompleted;
        boolean enabled = !running;

        // 单点模式只显示「开始模拟」，路线模式只显示「开始路线」，避免误操作。
        startMockButton.setVisibility(!running && !routeMode ? View.VISIBLE : View.GONE);
        stopMockButton.setVisibility(running ? View.VISIBLE : View.GONE);
        startRouteButton.setVisibility(!running && routeMode ? View.VISIBLE : View.GONE);
        pauseRouteButton.setVisibility(running && routeMode && !completed
                ? View.VISIBLE : View.GONE);
        setChildrenEnabled(modeToggle, enabled);
        routeSpeedSeekBar.setEnabled(enabled);
        routeSpeedInput.setEnabled(enabled);
        routeLoopSwitch.setEnabled(enabled);
        clearRouteButton.setEnabled(enabled);
        saveRouteButton.setEnabled(enabled);
        locationName.setEnabled(enabled);
        routeSection.setVisibility(routeMode ? View.VISIBLE : View.GONE);

        if (running) {
            if (state.location != null) {
                selectedCoordinates.setText(FormatUtil.formatCoordinate(context, state.location));
                runJs("setTarget(" + state.location.latitude + "," + state.location.longitude + ",'');");
            }
            if (completed) {
                mockStatus.setText(context.getString(R.string.status_route_completed,
                        state.progressPercent()));
                mockStatus.setTextColor(ContextCompat.getColor(context, R.color.color_status_paused));
            } else if (state.paused) {
                mockStatus.setText(R.string.status_paused);
                mockStatus.setTextColor(ContextCompat.getColor(context, R.color.color_status_paused));
            } else {
                if (routeMode) {
                    mockStatus.setText(context.getString(R.string.status_running_progress,
                            state.progressPercent()));
                } else {
                    mockStatus.setText(R.string.status_running);
                }
                mockStatus.setTextColor(ContextCompat.getColor(context, R.color.color_status_running));
            }
            pauseRouteButton.setText(state.paused
                    ? R.string.action_resume_route : R.string.action_pause_route);
            return;
        }

        GeoPoint selected = host.getSelectedPoint();
        selectedCoordinates.setText(selected == null
                ? context.getString(R.string.selected_location_none)
                : FormatUtil.formatCoordinate(context, selected));
        if (state != null && state.error != null) {
            mockStatus.setText(context.getString(R.string.status_failed, state.error));
            mockStatus.setTextColor(ContextCompat.getColor(context, R.color.color_self_check_fail));
        } else {
            mockStatus.setText(R.string.status_idle);
            mockStatus.setTextColor(ContextCompat.getColor(context, R.color.color_status_idle));
        }
        pauseRouteButton.setText(R.string.action_pause_route);
    }

    private static void setChildrenEnabled(ViewGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
        }
    }

    private void setSpeedUi(double speedKmh) {
        int value = (int) Math.round(speedKmh);
        if (value < 0) {
            value = 0;
        } else if (value > 300) {
            value = 300;
        }
        updatingSpeed = true;
        routeSpeedSeekBar.setProgress(value);
        routeSpeedInput.setText(String.valueOf(value));
        routeSpeedValue.setText(context.getString(R.string.speed_value, value));
        updatingSpeed = false;
    }

    private void setLoopUi(boolean loop) {
        updatingLoop = true;
        routeLoopSwitch.setChecked(loop);
        updatingLoop = false;
    }

    // ------------------------------------------------------------ 搜索

    private void setupSearch() {
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitSearch();
            }
        });
        clearSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchInput.setText("");
                hideSearchResults();
            }
        });
        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    submitSearch();
                    return true;
                }
                return false;
            }
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void submitSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        GeoPoint coordinate = parseCoordinate(query);
        if (coordinate != null) {
            hideSearchResults();
            hideKeyboard();
            host.onAddressSelected("", coordinate.latitude, coordinate.longitude);
            return;
        }
        searchButton.setEnabled(false);
        nominatim.search(query, new NominatimClient.Callback() {
            @Override
            public void onResults(List<SearchResult> results) {
                searchButton.setEnabled(true);
                if (results.isEmpty()) {
                    hideSearchResults();
                    host.showToast(R.string.search_no_results);
                    return;
                }
                searchAdapter.submit(results);
                searchResultsCard.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure() {
                searchButton.setEnabled(true);
                hideSearchResults();
                host.showToast(R.string.search_failed_check_network);
            }
        });
    }

    /** 解析「纬度,经度」（逗号、分号或空白分隔）；不是坐标则返回 null。 */
    private static GeoPoint parseCoordinate(String text) {
        String normalized = text.replace('，', ',');
        String[] parts = normalized.split("[,;\\s]+");
        if (parts.length != 2) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            GeoPoint point = new GeoPoint(latitude, longitude);
            return point.isValid() ? point : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void hideSearchResults() {
        searchResultsCard.setVisibility(View.GONE);
        searchAdapter.submit(null);
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    // ------------------------------------------------------------ 模式与路线控件

    private void setupModeToggle() {
        modeToggle.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (!isChecked || suppressModeCallback) {
                    return;
                }
                MockState.Mode next = checkedId == R.id.btn_route_mode
                        ? MockState.Mode.ROUTE : MockState.Mode.STATIC;
                MockState state = host.getMockState();
                if (state != null && state.running) {
                    host.showToast(R.string.mode_switch_running);
                    applyModeInternal(host.getMode());
                    return;
                }
                host.onModeChanged(next);
                applyModeInternal(next);
            }
        });
    }

    private void setupRouteControls() {
        routeSpeedSeekBar.setMax(300);
        routeSpeedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingSpeed) {
                    return;
                }
                updatingSpeed = true;
                routeSpeedInput.setText(String.valueOf(progress));
                routeSpeedValue.setText(context.getString(R.string.speed_value, progress));
                updatingSpeed = false;
                if (fromUser) {
                    host.onSpeedChanged(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        routeSpeedInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingSpeed) {
                    return;
                }
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    return;
                }
                double value;
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    return;
                }
                int clamped = (int) Math.round(Math.max(0.0, Math.min(300.0, value)));
                updatingSpeed = true;
                routeSpeedSeekBar.setProgress(clamped);
                routeSpeedValue.setText(context.getString(R.string.speed_value, clamped));
                updatingSpeed = false;
                host.onSpeedChanged(clamped);
            }
        });
        routeLoopSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (updatingLoop) {
                    return;
                }
                host.onLoopChanged(isChecked);
            }
        });
        setSpeedUi(host.getRouteSpeedKmh());
        setLoopUi(host.isRouteLoop());
    }

    private void setupButtons() {
        startMockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onStartMock();
            }
        });
        stopMockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onStopMock();
            }
        });
        startRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onStartRoute();
            }
        });
        pauseRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onToggleRoutePause();
            }
        });
        clearRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onClearRoute();
            }
        });
        saveRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onSaveRoute();
            }
        });
        myLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onMyLocation();
            }
        });
    }

    /** 释放 WebView 与网络线程；MainActivity 销毁时调用。 */
    public void destroy() {
        destroyed = true;
        nominatim.shutdown();
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.loadUrl("about:blank");
            webView.destroy();
        }
    }
}
