package io.github.mocklocation.oss;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.mocklocation.oss.core.CoordinateSystem;
import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.core.MockEngine;
import io.github.mocklocation.oss.core.MockState;
import io.github.mocklocation.oss.data.PlaceStore;
import io.github.mocklocation.oss.data.SettingsStore;
import io.github.mocklocation.oss.service.MockLocationService;
import io.github.mocklocation.oss.ui.CoordinateInputFragment;
import io.github.mocklocation.oss.ui.FormatUtil;
import io.github.mocklocation.oss.ui.MapPageController;
import io.github.mocklocation.oss.ui.PlaceListFragment;
import io.github.mocklocation.oss.ui.Refreshable;
import io.github.mocklocation.oss.ui.RoutesFragment;
import io.github.mocklocation.oss.ui.SelfCheckDialog;
import io.github.mocklocation.oss.ui.SettingsFragment;
import io.github.mocklocation.oss.ui.UiHost;
import io.github.mocklocation.oss.ui.UiPrefs;

/**
 * 主界面：抽屉导航 + 常驻地图页 + 五个 Fragment 页面。
 *
 * <p>本类是全局状态的唯一持有者：当前选中点、路线点与速度/循环、模拟服务绑定状态、设置项。
 * 各页面通过 {@link UiHost} 回调到这里，再由这里驱动 {@link MapPageController} 与服务。</p>
 *
 * <p>服务交互：START_MOCK / START_ROUTE / STOP_MOCK / TOGGLE_ROUTE_PAUSE 四个 action 用 startService 发送；
 * bindService 拿 {@link MockLocationService.LocalBinder} 订阅 {@link MockState}。</p>
 */
public class MainActivity extends AppCompatActivity
        implements UiHost, MockLocationService.Listener {

    private static final int REQUEST_LOCATION = 1101;
    private static final int REQUEST_NOTIFICATION = 1102;

    private static final int PENDING_NONE = 0;
    private static final int PENDING_MY_LOCATION = 1;
    private static final int PENDING_START_MOCK = 2;
    private static final int PENDING_START_ROUTE = 3;

    private static final String STATE_SELECTED_LAT = "state_selected_lat";
    private static final String STATE_SELECTED_LNG = "state_selected_lng";
    private static final String STATE_SELECTED_LABEL = "state_selected_label";
    private static final String STATE_ROUTE_LATS = "state_route_lats";
    private static final String STATE_ROUTE_LNGS = "state_route_lngs";
    private static final String STATE_MODE = "state_mode";
    private static final String STATE_SPEED = "state_speed";
    private static final String STATE_LOOP = "state_loop";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private FrameLayout fragmentContainer;
    private View mapPageRoot;
    private MapPageController mapPage;
    private Fragment currentPage;

    private PlaceStore placeStore;
    private SettingsStore settingsStore;
    private UiPrefs uiPrefs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final List<GeoPoint> routePoints = new ArrayList<>();

    private MockState mockState = MockState.idle();
    private MockLocationService mockService;
    private boolean serviceBound;
    private boolean startCountPending;
    private boolean routeCompletedNotified;
    private GeoPoint pendingHistoryPoint;
    private String pendingHistoryLabel;

    private GeoPoint selectedPoint;
    private String selectedLabel = "";
    private double routeSpeedKmh = 30.0;
    private boolean routeLoop;
    private MockState.Mode mode = MockState.Mode.STATIC;
    private int pendingAction = PENDING_NONE;

    private LocationListener singleUpdateListener;
    private boolean singleUpdateRequested;

    private final Runnable locationTimeout = new Runnable() {
        @Override
        public void run() {
            if (singleUpdateRequested) {
                cancelSingleLocation();
                showToast(R.string.location_unavailable);
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MockLocationService.LocalBinder binder = (MockLocationService.LocalBinder) service;
            mockService = binder.getService();
            serviceBound = true;
            mockService.addListener(MainActivity.this);
            onMockStateChanged(mockService.getState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mockService != null) {
                mockService.removeListener(MainActivity.this);
            }
            mockService = null;
            serviceBound = false;
        }
    };

    // ------------------------------------------------------------ 生命周期

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        fragmentContainer = findViewById(R.id.fragment_container);
        mapPageRoot = findViewById(R.id.map_page);

        placeStore = new PlaceStore(this);
        settingsStore = new SettingsStore(this);
        uiPrefs = new UiPrefs(this);

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        mapPage = new MapPageController(this, mapPageRoot);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.nav_open_drawer, R.string.nav_close_drawer);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        return handleNavigation(item);
                    }
                });

        mapPage.applyMode(mode);
        mapPage.applyTileLayer(settingsStore.getMapProvider());
        if (selectedPoint != null) {
            mapPage.showSelected(selectedPoint, selectedLabel);
        }
        mapPage.renderRoute(routePoints, routeSpeedKmh, routeLoop);
        showMapPage();
        handleVerifyIntent(getIntent());

        requestNotificationPermissionIfNeeded();
        mapPageRoot.postDelayed(new Runnable() {
            @Override
            public void run() {
                maybeShowSelfCheck();
            }
        }, 800L);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MockLocationService.class);
        if (!serviceBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        if (serviceBound) {
            if (mockService != null) {
                mockService.removeListener(this);
            }
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {
                // 已解绑时忽略。
            }
            serviceBound = false;
            mockService = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        cancelSingleLocation();
        if (mapPage != null) {
            mapPage.destroy();
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (currentPage != null) {
            showMapPage();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedPoint != null) {
            outState.putDouble(STATE_SELECTED_LAT, selectedPoint.latitude);
            outState.putDouble(STATE_SELECTED_LNG, selectedPoint.longitude);
            outState.putString(STATE_SELECTED_LABEL, selectedLabel);
        }
        double[] latitudes = new double[routePoints.size()];
        double[] longitudes = new double[routePoints.size()];
        for (int i = 0; i < routePoints.size(); i++) {
            latitudes[i] = routePoints.get(i).latitude;
            longitudes[i] = routePoints.get(i).longitude;
        }
        outState.putDoubleArray(STATE_ROUTE_LATS, latitudes);
        outState.putDoubleArray(STATE_ROUTE_LNGS, longitudes);
        outState.putString(STATE_MODE, mode.name());
        outState.putDouble(STATE_SPEED, routeSpeedKmh);
        outState.putBoolean(STATE_LOOP, routeLoop);
    }

    private void restoreState(Bundle state) {
        if (state.containsKey(STATE_SELECTED_LAT)) {
            selectedPoint = new GeoPoint(state.getDouble(STATE_SELECTED_LAT, 0.0),
                    state.getDouble(STATE_SELECTED_LNG, 0.0));
            String label = state.getString(STATE_SELECTED_LABEL, "");
            selectedLabel = label == null ? "" : label;
        }
        double[] latitudes = state.getDoubleArray(STATE_ROUTE_LATS);
        double[] longitudes = state.getDoubleArray(STATE_ROUTE_LNGS);
        routePoints.clear();
        if (latitudes != null && longitudes != null && latitudes.length == longitudes.length) {
            for (int i = 0; i < latitudes.length; i++) {
                routePoints.add(new GeoPoint(latitudes[i], longitudes[i]));
            }
        }
        mode = "ROUTE".equals(state.getString(STATE_MODE)) ? MockState.Mode.ROUTE : MockState.Mode.STATIC;
        routeSpeedKmh = state.getDouble(STATE_SPEED, 30.0);
        routeLoop = state.getBoolean(STATE_LOOP, false);
    }

    // ------------------------------------------------------------ 页面导航

    private boolean handleNavigation(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.nav_map) {
            showMapPage();
        } else if (itemId == R.id.nav_coordinate_input) {
            showPage(new CoordinateInputFragment(), R.string.title_coordinate_input);
        } else if (itemId == R.id.nav_favorites) {
            showPage(PlaceListFragment.newInstance(true), R.string.title_favorites);
        } else if (itemId == R.id.nav_history) {
            showPage(PlaceListFragment.newInstance(false), R.string.title_history);
        } else if (itemId == R.id.nav_routes) {
            showPage(new RoutesFragment(), R.string.title_routes);
        } else if (itemId == R.id.nav_settings) {
            showPage(new SettingsFragment(), R.string.title_settings);
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showMapPage() {
        currentPage = null;
        Fragment existing = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (existing != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(existing).commitAllowingStateLoss();
        }
        fragmentContainer.setVisibility(View.GONE);
        mapPageRoot.setVisibility(View.VISIBLE);
        navigationView.setCheckedItem(R.id.nav_map);
        setToolbarTitle(R.string.title_location_simulator);
    }

    private void showPage(Fragment fragment, int titleRes) {
        currentPage = fragment;
        fragmentContainer.setVisibility(View.VISIBLE);
        mapPageRoot.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(titleRes);
    }

    private void setToolbarTitle(int titleRes) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titleRes);
        } else {
            setTitle(titleRes);
        }
    }

    @Override
    public void navigateToMap() {
        showMapPage();
    }

    @Override
    public void refreshPlaceLists() {
        if (currentPage instanceof Refreshable) {
            ((Refreshable) currentPage).refreshData();
        }
    }

    // ------------------------------------------------------------ 服务状态

    @Override
    public void onMockStateChanged(final MockState state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                applyMockState(state);
            }
        });
    }

    private void applyMockState(MockState state) {
        if (state == null) {
            state = MockState.idle();
        }
        boolean wasRunning = mockState.running;
        mockState = state;
        if (mapPage != null) {
            mapPage.updateState(state);
        }
        if (startCountPending && state.running) {
            startCountPending = false;
            if (pendingHistoryPoint != null) {
                rememberHistory(pendingHistoryPoint, pendingHistoryLabel);
                pendingHistoryPoint = null;
                pendingHistoryLabel = null;
            }
            int count = settingsStore.incrementSuccessfulRuns();
            if (count == 3 && !settingsStore.isReviewDismissed()) {
                showReviewDialog(count);
            }
        }
        if (state.running) {
            if (state.routeCompleted && !routeCompletedNotified) {
                routeCompletedNotified = true;
                showToast(R.string.route_completed_toast);
            }
        } else {
            startCountPending = false;
            routeCompletedNotified = false;
            pendingHistoryPoint = null;
            pendingHistoryLabel = null;
            if (state.error != null) {
                showToast(getString(R.string.status_failed, state.error));
            } else if (wasRunning) {
                showToast(R.string.mock_stopped);
            }
        }
    }

    private void showReviewDialog(int count) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_title)
                .setMessage(getString(R.string.review_message, count))
                .setPositiveButton(R.string.review_positive,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                settingsStore.setReviewDismissed(true);
                                showToast(R.string.review_thanks);
                            }
                        })
                .setNegativeButton(R.string.review_negative,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                settingsStore.setReviewDismissed(true);
                            }
                        })
                .show();
    }

    // ------------------------------------------------------------ 模拟控制

    @Override
    public void onStartMock() {
        if (mockState.running) {
            return;
        }
        if (!ensureStartReady(PENDING_START_MOCK)) {
            return;
        }
        if (selectedPoint == null) {
            showToast(R.string.error_no_selected_point);
            return;
        }
        String label = getUserLabel();
        selectedLabel = label;
        Intent intent = new Intent(this, MockLocationService.class)
                .setAction(MockLocationService.ACTION_START_MOCK)
                .putExtra(MockLocationService.EXTRA_LATITUDE, selectedPoint.latitude)
                .putExtra(MockLocationService.EXTRA_LONGITUDE, selectedPoint.longitude)
                .putExtra(MockLocationService.EXTRA_LABEL, label);
        startCountPending = true;
        pendingHistoryPoint = selectedPoint;
        pendingHistoryLabel = label;
        if (!startServiceSafely(intent)) {
            startCountPending = false;
            pendingHistoryPoint = null;
            pendingHistoryLabel = null;
        }
    }

    @Override
    public void onStartRoute() {
        if (mockState.running) {
            return;
        }
        if (!ensureStartReady(PENDING_START_ROUTE)) {
            return;
        }
        if (routePoints.size() < 2) {
            showToast(R.string.route_needs_two_points);
            return;
        }
        if (routeSpeedKmh <= 0.0 || routeSpeedKmh > 300.0) {
            showToast(R.string.error_speed_range);
            return;
        }
        int size = routePoints.size();
        double[] latitudes = new double[size];
        double[] longitudes = new double[size];
        for (int i = 0; i < size; i++) {
            latitudes[i] = routePoints.get(i).latitude;
            longitudes[i] = routePoints.get(i).longitude;
        }
        Intent intent = new Intent(this, MockLocationService.class)
                .setAction(MockLocationService.ACTION_START_ROUTE)
                .putExtra(MockLocationService.EXTRA_ROUTE_LATITUDES, latitudes)
                .putExtra(MockLocationService.EXTRA_ROUTE_LONGITUDES, longitudes)
                .putExtra(MockLocationService.EXTRA_ROUTE_SPEED_MPS, routeSpeedKmh / 3.6)
                .putExtra(MockLocationService.EXTRA_ROUTE_LOOP, routeLoop);
        startCountPending = true;
        pendingHistoryPoint = routePoints.get(0);
        pendingHistoryLabel = null;
        if (!startServiceSafely(intent)) {
            startCountPending = false;
            pendingHistoryPoint = null;
            pendingHistoryLabel = null;
        }
    }

    @Override
    public void onStopMock() {
        sendServiceAction(MockLocationService.ACTION_STOP_MOCK);
    }

    @Override
    public void onToggleRoutePause() {
        sendServiceAction(MockLocationService.ACTION_TOGGLE_ROUTE_PAUSE);
    }

    /**
     * 原软件的行为：点击开始模拟时先确认「已被选为模拟位置应用」与定位权限，
     * 不满足就弹自检/申请权限并中止本次启动；满足后再下发 Intent。
     */
    private boolean ensureStartReady(int pendingActionIfPermissionMissing) {
        if (!MockEngine.isSelectedMockApp(this)) {
            showSelfCheck();
            return false;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAction = pendingActionIfPermissionMissing;
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private void sendServiceAction(String action) {
        try {
            startService(new Intent(this, MockLocationService.class).setAction(action));
        } catch (Throwable t) {
            showToast(R.string.error_service_not_ready);
        }
    }

    private boolean startServiceSafely(Intent intent) {
        try {
            if (serviceBound && mockService != null) {
                startService(intent);
            } else {
                ContextCompat.startForegroundService(this, intent);
            }
            return true;
        } catch (Throwable t) {
            showToast(R.string.error_start_failed);
            return false;
        }
    }

    private String getUserLabel() {
        return mapPage != null ? mapPage.getLocationName() : "";
    }

    private void rememberHistory(final GeoPoint point, final String name) {
        if (point == null) {
            return;
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    placeStore.insertPlace(name == null ? "" : name, point,
                            PlaceStore.RecordType.HISTORY);
                    placeStore.trimHistory(200);
                } catch (Throwable ignored) {
                    // 历史记录失败不影响模拟。
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshPlaceLists();
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------ 地图与选点

    @Override
    public void onMapTap(double latitude, double longitude) {
        if (mockState.running) {
            showToast(R.string.error_stop_before_select);
            return;
        }
        GeoPoint point = new GeoPoint(latitude, longitude);
        if (mode == MockState.Mode.ROUTE) {
            routePoints.add(point);
            mapPage.renderRoute(routePoints, routeSpeedKmh, routeLoop);
            showToast(getString(R.string.route_point_added, routePoints.size()));
        } else {
            selectedPoint = point;
            selectedLabel = "";
            mapPage.showSelected(point, "");
        }
    }

    @Override
    public void onAddressSelected(String label, double latitude, double longitude) {
        if (mockState.running) {
            showToast(R.string.error_stop_before_select);
            return;
        }
        GeoPoint point = new GeoPoint(latitude, longitude);
        if (!point.isValid()) {
            showToast(R.string.search_coordinate_format_error);
            return;
        }
        if (mode == MockState.Mode.ROUTE) {
            routePoints.add(point);
            mapPage.renderRoute(routePoints, routeSpeedKmh, routeLoop);
            showToast(getString(R.string.route_point_added, routePoints.size()));
            return;
        }
        selectedPoint = point;
        selectedLabel = label == null ? "" : label;
        mapPage.showSelected(point, selectedLabel);
        mapPage.centerOn(point, 15);
        if (!selectedLabel.isEmpty()) {
            showToast(getString(R.string.search_result_selected, selectedLabel));
        }
    }

    @Override
    public void setSelectedPoint(GeoPoint point, String label) {
        if (mockState.running) {
            showToast(R.string.error_stop_before_select);
            return;
        }
        selectedPoint = point;
        selectedLabel = label == null ? "" : label;
        if (mapPage != null) {
            mapPage.showSelected(point, selectedLabel);
        }
    }

    @Override
    public void onModeChanged(MockState.Mode mode) {
        this.mode = mode;
    }

    @Override
    public void onSpeedChanged(double speedKmh) {
        routeSpeedKmh = speedKmh;
    }

    @Override
    public void onLoopChanged(boolean loop) {
        routeLoop = loop;
    }

    @Override
    public void onClearRoute() {
        routePoints.clear();
        if (mapPage != null) {
            mapPage.renderRoute(routePoints, routeSpeedKmh, routeLoop);
        }
        showToast(R.string.route_cleared);
    }

    @Override
    public void onSaveRoute() {
        if (routePoints.size() < 2) {
            showToast(R.string.route_needs_two_points);
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_input_text, null);
        final EditText input = content.findViewById(R.id.et_dialog_input);
        input.setHint(R.string.route_name_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_route_title)
                .setView(content)
                .setPositiveButton(R.string.action_ok,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                String typed = input.getText().toString().trim();
                                final String routeName = typed.isEmpty()
                                        ? getString(R.string.route_default_name, System.currentTimeMillis())
                                        : typed;
                                final List<GeoPoint> snapshot = new ArrayList<>(routePoints);
                                final double speed = routeSpeedKmh;
                                final boolean loop = routeLoop;
                                ioExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        boolean saved = true;
                                        try {
                                            placeStore.insertRoute(routeName, snapshot, speed, loop);
                                        } catch (Throwable t) {
                                            saved = false;
                                        }
                                        final boolean result = saved;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (result) {
                                                    showToast(getString(R.string.route_saved, routeName));
                                                    refreshPlaceLists();
                                                } else {
                                                    showToast(R.string.route_save_failed);
                                                }
                                            }
                                        });
                                    }
                                });
                            }
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ------------------------------------------------------------ 列表回调

    @Override
    public void onPlaceSelected(PlaceStore.PlaceRecord record) {
        if (record == null || record.point == null) {
            return;
        }
        if (mockState.running) {
            showToast(R.string.error_stop_before_select);
            return;
        }
        mode = MockState.Mode.STATIC;
        mapPage.applyMode(mode);
        selectedPoint = record.point;
        selectedLabel = FormatUtil.placeDisplayName(this, record.name, record.id);
        mapPage.showSelected(selectedPoint, selectedLabel);
        mapPage.centerOn(selectedPoint, 15);
        navigateToMap();
    }

    @Override
    public void onRouteSelected(PlaceStore.SavedRoute route) {
        if (route == null || route.points == null || route.points.size() < 2) {
            showToast(R.string.route_invalid);
            return;
        }
        if (mockState.running) {
            showToast(R.string.error_stop_before_select);
            return;
        }
        mode = MockState.Mode.ROUTE;
        routePoints.clear();
        routePoints.addAll(route.points);
        routeSpeedKmh = route.speedKmh;
        routeLoop = route.loop;
        mapPage.applyMode(mode);
        mapPage.renderRoute(routePoints, routeSpeedKmh, routeLoop);
        mapPage.fitRoute();
        navigateToMap();
        showToast(getString(R.string.route_loaded,
                FormatUtil.routeDisplayName(this, route.name, route.id)));
    }

    // ------------------------------------------------------------ 我的位置

    @Override
    public void onMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAction = PENDING_MY_LOCATION;
            requestLocationPermission();
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            showToast(R.string.location_unavailable);
            return;
        }
        Location best = null;
        String[] providers = new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
        for (String provider : providers) {
            try {
                Location last = manager.getLastKnownLocation(provider);
                if (last != null && (best == null || last.getTime() > best.getTime())) {
                    best = last;
                }
            } catch (SecurityException ignored) {
                // 没授权就读不到，继续尝试下一个 provider。
            } catch (Throwable ignored) {
                // 个别设备 provider 不可用，忽略。
            }
        }
        if (best != null) {
            applyMyLocation(manager, best);
            return;
        }
        requestSingleLocation(manager);
    }

    private void requestSingleLocation(final LocationManager manager) {
        if (singleUpdateRequested) {
            return;
        }
        singleUpdateRequested = true;
        showToast(R.string.location_requesting);
        singleUpdateListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                finishSingleLocation(manager, location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        try {
            String provider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (!manager.isProviderEnabled(provider)) {
                singleUpdateRequested = false;
                singleUpdateListener = null;
                showToast(R.string.location_unavailable);
                return;
            }
            manager.requestSingleUpdate(provider, singleUpdateListener, Looper.getMainLooper());
        } catch (Throwable t) {
            singleUpdateRequested = false;
            singleUpdateListener = null;
            showToast(R.string.location_unavailable);
            return;
        }
        mainHandler.postDelayed(locationTimeout, 12000L);
    }

    private void finishSingleLocation(LocationManager manager, Location location) {
        singleUpdateRequested = false;
        mainHandler.removeCallbacks(locationTimeout);
        if (singleUpdateListener != null) {
            try {
                manager.removeUpdates(singleUpdateListener);
            } catch (Throwable ignored) {
                // 已停止更新时忽略。
            }
            singleUpdateListener = null;
        }
        applyMyLocation(manager, location);
    }

    private void cancelSingleLocation() {
        if (!singleUpdateRequested) {
            return;
        }
        singleUpdateRequested = false;
        mainHandler.removeCallbacks(locationTimeout);
        if (singleUpdateListener != null) {
            try {
                LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (manager != null) {
                    manager.removeUpdates(singleUpdateListener);
                }
            } catch (Throwable ignored) {
                // 忽略。
            }
            singleUpdateListener = null;
        }
    }

    private void applyMyLocation(LocationManager manager, Location location) {
        if (location == null) {
            showToast(R.string.location_unavailable);
            return;
        }
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (!point.isValid()) {
            showToast(R.string.location_unavailable);
            return;
        }
        mode = MockState.Mode.STATIC;
        mapPage.applyMode(mode);
        selectedPoint = point;
        selectedLabel = "";
        mapPage.showSelected(point, "");
        mapPage.showHere(point);
        mapPage.centerOn(point, 16);
        showToast(R.string.location_found);
    }

    // ------------------------------------------------------------ 权限

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_LOCATION);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted && pendingAction == PENDING_MY_LOCATION) {
                pendingAction = PENDING_NONE;
                onMyLocation();
            } else if (granted && pendingAction == PENDING_START_MOCK) {
                pendingAction = PENDING_NONE;
                onStartMock();
            } else if (granted && pendingAction == PENDING_START_ROUTE) {
                pendingAction = PENDING_NONE;
                onStartRoute();
            } else if (!granted) {
                pendingAction = PENDING_NONE;
                showToast(R.string.location_permission_denied);
            }
        }
    }

    // ------------------------------------------------------------ 仅 debug 构件的自动化验证入口

    /**
     * 只在 debug 构件里生效的指令入口，用于设备/模拟器上的自动化验收（见 tools/device-verify.sh）。
     *
     * 通过 `adb shell am start -n <pkg>/.MainActivity -a io.github.mocklocation.oss.VERIFY
     * --es command start_static --ed latitude 39.9 --ed longitude 116.4` 触发；release 构件不会响应。
     */
    private void handleVerifyIntent(Intent intent) {
        if (!BuildConfig.DEBUG || intent == null) return;
        if (!"io.github.mocklocation.oss.VERIFY".equals(intent.getAction())) return;
        String command = intent.getStringExtra("command");
        if (command == null || mapPage == null) return;
        switch (command) {
            case "start_static": {
                double latitude = intent.getDoubleExtra("latitude", Double.NaN);
                double longitude = intent.getDoubleExtra("longitude", Double.NaN);
                if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                    showToast(R.string.error_no_selected_point);
                    return;
                }
                mode = MockState.Mode.STATIC;
                onModeChanged(mode);
                setSelectedPoint(new GeoPoint(latitude, longitude), "verify");
                onStartMock();
                break;
            }
            case "start_route": {
                String encoded = intent.getStringExtra("route");
                if (encoded == null || encoded.isEmpty()) return;
                List<GeoPoint> parsed = new ArrayList<>();
                for (String pair : encoded.split(";")) {
                    int comma = pair.indexOf(',');
                    if (comma <= 0) continue;
                    try {
                        parsed.add(new GeoPoint(Double.parseDouble(pair.substring(0, comma).trim()),
                                Double.parseDouble(pair.substring(comma + 1).trim())));
                    } catch (NumberFormatException ignored) { }
                }
                if (parsed.size() < 2) {
                    showToast(R.string.route_needs_two_points);
                    return;
                }
                routePoints.clear();
                routePoints.addAll(parsed);
                routeSpeedKmh = intent.getDoubleExtra("speed_kmh", 30.0);
                routeLoop = intent.getBooleanExtra("loop", false);
                mode = MockState.Mode.ROUTE;
                onModeChanged(mode);
                mapPage.applyMode(mode);
                mapPage.renderRoute(routePoints, routeSpeedKmh, routeLoop);
                onStartRoute();
                break;
            }
            case "toggle_pause":
                onToggleRoutePause();
                break;
            case "stop":
                onStopMock();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleVerifyIntent(intent);
    }

    // ------------------------------------------------------------ 自检与关于

    private void maybeShowSelfCheck() {
        String today = UiPrefs.today();
        if (today.equals(uiPrefs.getLastSelfCheckDay())) {
            return;
        }
        uiPrefs.setLastSelfCheckDay(today);
        showSelfCheck();
    }

    @Override
    public void openSelfCheck() {
        showSelfCheck();
    }

    private void showSelfCheck() {
        SelfCheckDialog.show(this, settingsStore, new Runnable() {
            @Override
            public void run() {
                showToast(R.string.self_check_completed);
            }
        });
    }

    @Override
    public void openAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    // ------------------------------------------------------------ UiHost 取值

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void runOnMainThread(Runnable action) {
        runOnUiThread(action);
    }

    @Override
    public void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showToast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public MockState getMockState() {
        return mockState;
    }

    @Override
    public MockState.Mode getMode() {
        return mode;
    }

    @Override
    public GeoPoint getSelectedPoint() {
        return selectedPoint;
    }

    @Override
    public String getSelectedLabel() {
        return selectedLabel;
    }

    @Override
    public List<GeoPoint> getRoutePoints() {
        return routePoints;
    }

    @Override
    public double getRouteSpeedKmh() {
        return routeSpeedKmh;
    }

    @Override
    public boolean isRouteLoop() {
        return routeLoop;
    }

    @Override
    public String getMapProvider() {
        return settingsStore.getMapProvider();
    }

    @Override
    public void setMapProvider(String provider) {
        settingsStore.setMapProvider(provider);
        if (mapPage != null) {
            mapPage.applyTileLayer(provider);
        }
    }

    @Override
    public CoordinateSystem getCoordinateSystem() {
        try {
            return CoordinateSystem.valueOf(settingsStore.getCoordinateSystem());
        } catch (IllegalArgumentException e) {
            return CoordinateSystem.WGS84;
        }
    }

    @Override
    public void setCoordinateSystem(CoordinateSystem system) {
        if (system != null) {
            settingsStore.setCoordinateSystem(system.name());
        }
    }

    @Override
    public SettingsStore getSettingsStore() {
        return settingsStore;
    }

    @Override
    public PlaceStore getPlaceStore() {
        return placeStore;
    }
}
