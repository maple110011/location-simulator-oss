package io.github.mocklocation.oss.ui;

/** 可重新加载数据的列表页；MainActivity 在数据变化后调用。 */
public interface Refreshable {
    void refreshData();
}
