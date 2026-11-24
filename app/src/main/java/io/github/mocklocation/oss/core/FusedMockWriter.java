package io.github.mocklocation.oss.core;

/** Fused（GMS）mock 写入端的抽象：生产实现走反射调用 Play 服务，测试实现可记录调用。 */
public interface FusedMockWriter {
    boolean write(GeoPoint point, LocationFields fields);

    void disable();
}
