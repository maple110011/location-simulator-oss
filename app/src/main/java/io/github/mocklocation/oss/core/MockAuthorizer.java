package io.github.mocklocation.oss.core;

/** 「本应用是否已被选为模拟位置应用」的判定抽象；生产实现查 AppOps，测试实现可固定返回值。 */
public interface MockAuthorizer {
    boolean isMockAppSelected();
}
