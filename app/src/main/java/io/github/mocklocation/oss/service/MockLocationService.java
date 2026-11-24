package io.github.mocklocation.oss.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.mocklocation.oss.MainActivity;
import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.core.MockEngine;
import io.github.mocklocation.oss.core.MockInjector;
import io.github.mocklocation.oss.core.MockState;
import io.github.mocklocation.oss.core.SimulationEngine;

/**
 * 前台模拟服务：持有 1 秒 tick 循环、路线推进器与注入引擎，是「正在模拟」这件事的唯一事实来源。
 *
 * 服务行为：
 * - 四个 action：START_MOCK / START_ROUTE / STOP_MOCK / TOGGLE_ROUTE_PAUSE；
 * - tick 固定 1000 ms，位移用 elapsedRealtime 真实时间差累计；
 * - 暂停或路线走完时仍每秒注入，但注入速度为 0；非循环走完 isRunning 仍为 true（holding final location）；
 * - 运行中 onStartCommand 返回 START_REDELIVER_INTENT，进程被杀后系统重投 Intent 自动恢复；
 * - 运行期间持 PARTIAL_WAKE_LOCK，前台通知 id=1001，每秒刷新。
 */
public class MockLocationService extends Service {
    private static final String TAG = "MockLocationService";

    public static final String ACTION_START_MOCK = "io.github.mocklocation.oss.START_MOCK";
    public static final String ACTION_START_ROUTE = "io.github.mocklocation.oss.START_ROUTE";
    public static final String ACTION_STOP_MOCK = "io.github.mocklocation.oss.STOP_MOCK";
    public static final String ACTION_TOGGLE_ROUTE_PAUSE = "io.github.mocklocation.oss.TOGGLE_ROUTE_PAUSE";

    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_LABEL = "location_name";
    public static final String EXTRA_ROUTE_LATITUDES = "route_latitudes";
    public static final String EXTRA_ROUTE_LONGITUDES = "route_longitudes";
    public static final String EXTRA_ROUTE_SPEED_MPS = "route_speed_mps";
    public static final String EXTRA_ROUTE_LOOP = "route_loop";

    public static final String CHANNEL_ID = "mock_location_channel";
    public static final int NOTIFICATION_ID = 1001;
    private static final long TICK_INTERVAL_MS = 1000L;

    /** 状态订阅者；UI 绑定服务后注册，服务每个 tick 与状态变化时回调。 */
    public interface Listener {
        void onMockStateChanged(MockState state);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LocalBinder binder = new LocalBinder();
    private final Runnable tickRunnable = this::onTick;

    private MockEngine engine;
    private SimulationEngine simulation = new SimulationEngine();
    private MockState state = MockState.idle();
    private PowerManager.WakeLock wakeLock;
    private boolean foregroundStarted;

    public final class LocalBinder extends Binder {
        public MockLocationService getService() {
            return MockLocationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = createMockEngine();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW); // 渠道 importance=LOW
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /** 供测试替换注入引擎；生产默认使用 Android 实现。 */
    protected MockEngine createMockEngine() {
        return new MockEngine(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            // 系统重建服务时没有 Intent：只有仍在运行中才重新拉起前台。
            if (state.running) {
                ensureForeground();
                engine.ensureTestProviders();
                scheduleTick(0L);
                return START_REDELIVER_INTENT;
            }
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        switch (action) {
            case ACTION_START_MOCK:
                // 通过 startForegroundService 启动时，系统要求 5 秒内必须 startForeground；
                // 必须在参数校验之前先起前台通知，否则校验失败路径会触发
                // Context.startForegroundService() did not then call Service.startForeground()。
                ensureForeground();
                if (!intent.hasExtra(EXTRA_LATITUDE) || !intent.hasExtra(EXTRA_LONGITUDE)) {
                    // 缺参数时直接退出，不报错。
                    stopSelf(startId);
                    return START_NOT_STICKY;
                }
                startSingleMock(new GeoPoint(intent.getDoubleExtra(EXTRA_LATITUDE, 0.0),
                        intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)),
                        intent.getStringExtra(EXTRA_LABEL));
                break;
            case ACTION_START_ROUTE:
                ensureForeground();
                startRoute(intent.getDoubleArrayExtra(EXTRA_ROUTE_LATITUDES),
                        intent.getDoubleArrayExtra(EXTRA_ROUTE_LONGITUDES),
                        intent.getDoubleExtra(EXTRA_ROUTE_SPEED_MPS, 0.0),
                        intent.getBooleanExtra(EXTRA_ROUTE_LOOP, false));
                break;
            case ACTION_STOP_MOCK:
                stopSimulation(null);
                break;
            case ACTION_TOGGLE_ROUTE_PAUSE:
                toggleRoutePause();
                break;
            default:
                break;
        }
        return state.running ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

    // ------------------------------------------------------------ 启动

    private void startSingleMock(GeoPoint point, String label) {
        if (!checkPreconditions()) return;
        simulation.startStatic(point, label, SystemClock.elapsedRealtime());
        state = MockState.staticLocation(point, label);
        ensureForeground();
        acquireWakeLock();
        scheduleTick(0L);   // startForeground 之后立刻注入第一次
        publish();
    }

    private void startRoute(double[] lats, double[] lons, double speedMps, boolean loopRoute) {
        if (!checkPreconditions()) return;
        if (lats == null || lons == null) {
            fail(getString(R.string.route_invalid));
            return;
        }
        int count = Math.min(lats.length, lons.length); // 纬度/经度按较短数组配对
        if (count < 2 || speedMps <= 0.0) {
            fail(getString(R.string.route_invalid));
            return;
        }
        List<GeoPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) points.add(new GeoPoint(lats[i], lons[i]));
        long now = SystemClock.elapsedRealtime();
        try {
            simulation.startRoute(points, speedMps, loopRoute, now);
        } catch (IllegalArgumentException e) {
            fail(getString(R.string.route_invalid));
            return;
        }
        SimulationEngine.Tick first = simulation.tick(now);
        GeoPoint firstPoint = first == null ? points.get(0) : first.point;
        state = MockState.route(speedMps, firstPoint, 0.0, simulation.getTotalMeters(), false, false);
        ensureForeground();
        acquireWakeLock();
        scheduleTick(0L);
        publish();
    }

    /** 开始模拟前的硬性前置条件：必须已被系统选为模拟位置应用。 */
    private boolean checkPreconditions() {
        if (!MockEngine.isSelectedMockApp(this)) {
            fail(getString(R.string.error_not_mock_app));
            return false;
        }
        // 先把 gps/network 两个测试 provider 建立并启用，之后每秒 setTestProviderLocation 才有落点。
        if (!engine.ensureTestProviders()) {
            fail(getString(R.string.error_injection_failed));
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ tick

    private void onTick() {
        if (!state.running) return;
        SimulationEngine.Tick tick = simulation.tick(SystemClock.elapsedRealtime());
        if (tick == null) return;
        if (simulation.getMode() == SimulationEngine.Mode.STATIC) {
            state = MockState.staticLocation(tick.point, state.label);
        } else {
            state = MockState.route(simulation.getSpeedMps(), tick.point, simulation.getTraveledMeters(),
                    simulation.getTotalMeters(), simulation.isPaused(), tick.completed);
        }
        MockInjector.InjectionResult result = engine.inject(tick.point, tick.speedMps, tick.bearingDegrees);
        if (!result.success) {
            fail(result.error == null ? getString(R.string.error_injection_failed) : result.error);
            return;
        }
        updateNotification();
        publish();
        scheduleTick(TICK_INTERVAL_MS);
    }

    private void scheduleTick(long delayMs) {
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, delayMs);
    }

    /** 供 UI 与通知 action 复用的暂停切换入口。 */
    public void togglePauseFromUi() {
        toggleRoutePause();
    }

    private void toggleRoutePause() {
        // 只有「路线模式 + 运行中 + 未走完」才允许暂停，单点模式无效。
        if (simulation.getMode() != SimulationEngine.Mode.ROUTE || !state.running || state.routeCompleted) return;
        simulation.togglePause(SystemClock.elapsedRealtime());
        state = state.withPaused(simulation.isPaused());
        updateNotification();
        publish();
    }

    private void stopSimulation(String error) {
        handler.removeCallbacks(tickRunnable);
        if (engine != null) engine.cleanup();
        releaseWakeLock();
        simulation.stop();
        state = error == null ? MockState.idle() : MockState.failed(error);
        if (foregroundStarted) {
            stopForeground(true);
            foregroundStarted = false;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
        publish();
        stopSelf();
    }

    private void fail(String message) {
        Log.e(TAG, "mock failed: " + message);
        stopSimulation(message);
    }

    private void publish() {
        for (Listener listener : listeners) {
            try { listener.onMockStateChanged(state); } catch (Throwable t) { Log.w(TAG, "listener failed", t); }
        }
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
        if (listener != null) listener.onMockStateChanged(state);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public MockState getState() { return state; }

    public boolean isRouteMode() { return simulation.getMode() == SimulationEngine.Mode.ROUTE; }

    // ------------------------------------------------------------ 通知

    private void ensureForeground() {
        if (foregroundStarted) {
            updateNotification();
            return;
        }
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foregroundStarted = true;
    }

    private void updateNotification() {
        if (!foregroundStarted) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        boolean routeMode = state.mode == MockState.Mode.ROUTE;
        String title = routeMode
                ? getString(R.string.notification_route_title)
                : getString(R.string.notification_mock_title);
        String text;
        if (state.location == null) {
            text = getString(R.string.notification_fetching);
        } else {
            String coords = String.format(Locale.US, "%.6f, %.6f",
                    state.location.latitude, state.location.longitude);
            if (routeMode) {
                // 路线通知正文："%1$.1f km/h · %2$s"
                text = getString(R.string.notification_route_status, state.speedKmh(), coords);
            } else if (state.label != null && !state.label.isEmpty()) {
                text = state.label + " · " + coords;
            } else {
                text = coords;
            }
        }

        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)        // FLAG_ONGOING_EVENT
                .setOnlyAlertOnce(true)  // 每秒刷新不响铃
                .setShowWhen(false)
                .setContentIntent(contentIntent());

        if (routeMode && !state.routeCompleted) {
            builder.addAction(new Notification.Action.Builder(
                    state.paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                    getString(state.paused
                            ? R.string.notification_resume_route
                            : R.string.notification_pause_route),
                    serviceIntent(ACTION_TOGGLE_ROUTE_PAUSE, 1)).build());
        }
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_stop),
                serviceIntent(ACTION_STOP_MOCK, 2)).build());
        return builder.build();
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MockLocationService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ------------------------------------------------------------ 电源与生命周期

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":MockLocation");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        if (engine != null) engine.cleanup();
        releaseWakeLock();
        super.onDestroy();
    }
}
