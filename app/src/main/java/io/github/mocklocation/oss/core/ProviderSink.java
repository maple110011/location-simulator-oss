package io.github.mocklocation.oss.core;

/**
 * 模拟定位写入端。生产实现是 AndroidLocationProviderSink（操作 LocationManager 与 GMS），
 * 测试实现只记录调用，从而可以在纯 JVM 上验证「注入参数与每秒采样」是否符合原软件规格。
 */
public interface ProviderSink {
    boolean addTestProvider(ProviderSpec spec);

    boolean setTestProviderEnabled(String name, boolean enabled);

    boolean removeTestProvider(String name);

    boolean setTestProviderStatus(String name, int status, long timeMillis);

    boolean setTestProviderLocation(String name, LocationFields fields);
}
