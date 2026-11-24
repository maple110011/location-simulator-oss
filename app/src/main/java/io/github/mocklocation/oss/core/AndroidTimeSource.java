package io.github.mocklocation.oss.core;

import android.os.SystemClock;

/** 生产实现：wall clock 用 System.currentTimeMillis，tick 时间用 SystemClock.elapsedRealtimeNanos。 */
public final class AndroidTimeSource implements TimeSource {
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long elapsedRealtimeNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }
}
