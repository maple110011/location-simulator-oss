package io.github.mocklocation.oss.ui;

import android.content.Context;

import java.util.List;

import io.github.mocklocation.oss.core.CoordinateSystem;
import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.core.MockState;
import io.github.mocklocation.oss.data.PlaceStore;
import io.github.mocklocation.oss.data.SettingsStore;

/**
 * 页面（Fragment / 地图页控制器）与 MainActivity 之间的唯一契约。
 *
 * <p>所有会改变全局状态的交互（选点、启动模拟、载入路线、设置项）都通过本接口回到 MainActivity，
 * 由 MainActivity 统一维护“当前选中点 / 路线点 / 模拟状态”，避免各页面各自持有一份状态。</p>
 */
public interface UiHost {

    Context getContext();

    void runOnMainThread(Runnable action);

    void showToast(int resId);

    void showToast(String message);

    // ------------------------------------------------------------ 当前状态

    MockState getMockState();

    /** 当前编辑模式（单点 / 路线）。 */
    MockState.Mode getMode();

    GeoPoint getSelectedPoint();

    String getSelectedLabel();

    /** 设置当前选中位置（坐标为 WGS84）。 */
    void setSelectedPoint(GeoPoint point, String label);

    /** 地址搜索结果被点选：设置选中位置并在地图上居中。 */
    void onAddressSelected(String label, double latitude, double longitude);

    List<GeoPoint> getRoutePoints();

    double getRouteSpeedKmh();

    boolean isRouteLoop();

    // ------------------------------------------------------------ 地图与模拟

    void onMapTap(double latitude, double longitude);

    void onModeChanged(MockState.Mode mode);

    void onSpeedChanged(double speedKmh);

    void onLoopChanged(boolean loop);

    void onStartMock();

    void onStopMock();

    void onStartRoute();

    void onToggleRoutePause();

    void onClearRoute();

    void onSaveRoute();

    void onMyLocation();

    // ------------------------------------------------------------ 列表页回调

    void onPlaceSelected(PlaceStore.PlaceRecord record);

    void onRouteSelected(PlaceStore.SavedRoute route);

    void navigateToMap();

    /** 让当前可见列表页重新查库。 */
    void refreshPlaceLists();

    // ------------------------------------------------------------ 设置

    String getMapProvider();

    void setMapProvider(String provider);

    CoordinateSystem getCoordinateSystem();

    void setCoordinateSystem(CoordinateSystem system);

    SettingsStore getSettingsStore();

    PlaceStore getPlaceStore();

    void openSelfCheck();

    void openAbout();
}
