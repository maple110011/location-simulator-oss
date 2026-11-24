package io.github.mocklocation.oss.core;

/** 注入时间源，保证纯 Java 测试可以控制 tick 的时间字段。 */
public interface TimeSource {
    long currentTimeMillis();

    long elapsedRealtimeNanos();
}
